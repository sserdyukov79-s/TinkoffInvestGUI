package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.api.CandlesApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для анализа облигаций с расчётом метрик на основе исторических свечей
 * ✅ С расчётом среднедневного объёма торгов и фильтром по ликвидности
 */
public class BondsAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(BondsAnalysisService.class);

    private static final int DEFAULT_MIN_DAYS_TO_MATURITY = 3;
    private static final int DEFAULT_MAX_MONTHS_TO_MATURITY = 15;
    private static final int CANDLES_PERIOD_MONTHS = 4;

    /**
     * Фильтрует облигации по заданным критериям
     */
    public List<Bond> filterBonds(List<Bond> bonds, BondsFilterCriteria criteria) {
        log.info("Фильтрация {} облигаций по критериям: {}", bonds.size(), criteria);

        LocalDate now = LocalDate.now();
        LocalDate minMaturityDate = now.plusDays(criteria.getMinDaysToMaturity());
        LocalDate maxMaturityDate = now.plusMonths(criteria.getMaxMonthsToMaturity());

        log.info("📅 Период фильтрации: от {} до {}", minMaturityDate, maxMaturityDate);

        // Счётчики для статистики
        int[] stats = new int[6]; // [total, currency, amort, maturity, dlong, risk]
        stats[0] = bonds.size();

        List<Bond> filtered = bonds.stream()
                .filter(bond -> {
                    // 1. Фильтр по валюте номинала
                    if (criteria.getNominalCurrency() != null && !criteria.getNominalCurrency().isEmpty()) {
                        String bondCurrency = bond.getNominal() != null ? bond.getNominal().getCurrency() : "";
                        if (!bondCurrency.equalsIgnoreCase(criteria.getNominalCurrency())) {
                            stats[1]++;
                            return false;
                        }
                    }

                    // 2. Фильтр: без амортизации
                    if (criteria.isWithoutAmortization()) {
                        if (bond.getAmortizationFlag()) {
                            stats[2]++;
                            return false;
                        }
                    }

                    // 3. Фильтр по сроку до погашения
                    if (bond.hasMaturityDate()) {
                        long maturitySeconds = bond.getMaturityDate().getSeconds();
                        LocalDate maturityDate = LocalDate.ofEpochDay(maturitySeconds / 86400);

                        if (maturityDate.isBefore(minMaturityDate) || maturityDate.isAfter(maxMaturityDate)) {
                            stats[3]++;
                            return false;
                        }
                    } else {
                        stats[3]++;
                        return false; // Нет даты погашения
                    }

                    // 4. Фильтр: требовать dlong > 0
                    if (criteria.isRequireDlong()) {
                        if (!bond.hasDlong()) {
                            stats[4]++;
                            return false;
                        }

                        // Правильный расчёт dlong с учётом дробной части
                        double dlongValue = bond.getDlong().getUnits() + bond.getDlong().getNano() / 1e9;
                        if (dlongValue <= 0) {
                            stats[4]++;
                            return false;
                        }
                    }

                    // 5. Фильтр: исключить высокий риск
                    if (criteria.isExcludeHighRisk()) {
                        RiskLevel riskLevel = bond.getRiskLevel();
                        if (riskLevel == RiskLevel.RISK_LEVEL_HIGH) {
                            stats[5]++;
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());

        // Выводим статистику фильтрации
        log.info("📊 Статистика фильтрации:");
        log.info("   Всего облигаций: {}", stats[0]);
        log.info("   ❌ Отброшено по валюте: {}", stats[1]);
        log.info("   ❌ Отброшено по амортизации: {}", stats[2]);
        log.info("   ❌ Отброшено по сроку погашения: {}", stats[3]);
        log.info("   ❌ Отброшено по dlong: {}", stats[4]);
        log.info("   ❌ Отброшено по риску: {}", stats[5]);
        log.info("   ✅ Прошло фильтрацию: {}", filtered.size());

        // Если результат пустой, выводим примеры первых 3 облигаций
        if (filtered.isEmpty() && !bonds.isEmpty()) {
            log.warn("⚠️ Ни одна облигация не прошла фильтрацию! Примеры первых 3 облигаций:");
            for (int i = 0; i < Math.min(3, bonds.size()); i++) {
                Bond b = bonds.get(i);
                String currency = b.getNominal() != null ? b.getNominal().getCurrency() : "NULL";
                boolean hasMaturity = b.hasMaturityDate();
                LocalDate maturity = null;
                if (hasMaturity) {
                    maturity = LocalDate.ofEpochDay(b.getMaturityDate().getSeconds() / 86400);
                }

                boolean hasDlong = b.hasDlong();
                double dlong = hasDlong ? (b.getDlong().getUnits() + b.getDlong().getNano() / 1e9) : 0;

                log.warn("   Облигация #{}: Ticker={}, Currency={}, Amort={}, Maturity={}, Dlong={}, Risk={}",
                        i + 1, b.getTicker(), currency, b.getAmortizationFlag(),
                        hasMaturity ? maturity : "НЕТ", dlong, b.getRiskLevel().name());
            }
        }

        return filtered;
    }

    /**
     * Анализирует облигации: загружает свечи и рассчитывает метрики
     * ✅ С фильтрацией по минимальному объёму торгов
     */
    public List<BondAnalysisResult> analyzeBonds(List<Bond> bonds, CandlesApiService candlesService, BondsFilterCriteria criteria) {
        log.info("Начало анализа {} облигаций", bonds.size());

        List<BondAnalysisResult> results = new ArrayList<>();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(CANDLES_PERIOD_MONTHS);

        int processed = 0;
        for (Bond bond : bonds) {
            try {
                processed++;
                if (processed % 10 == 0) {
                    log.info("Обработано: {}/{} ({}%)",
                            processed, bonds.size(), (processed * 100 / bonds.size()));
                }

                // Загружаем свечи за 4 месяца
                List<HistoricCandle> candles = candlesService.getCandles(
                        bond.getFigi(),
                        from,
                        to,
                        CandleInterval.CANDLE_INTERVAL_DAY
                );

                if (candles.isEmpty()) {
                    log.warn("Нет свечей для облигации: {}", bond.getTicker());
                    continue;
                }

                // Анализируем свечи
                BondAnalysisResult result = analyzeCandles(bond, candles);
                results.add(result);

            } catch (Exception e) {
                log.error("Ошибка анализа облигации {}: {}", bond.getTicker(), e.getMessage());
            }
        }

        log.info("Анализ завершён. Результатов до фильтрации по объёму: {}", results.size());

        // ✅ ФИЛЬТРАЦИЯ ПО МИНИМАЛЬНОМУ ОБЪЁМУ (если задан)
        if (criteria.getMinAvgDailyVolume() > 0) {
            int beforeFilter = results.size();
            results = results.stream()
                    .filter(r -> r.getAvgDailyVolume() >= criteria.getMinAvgDailyVolume())
                    .collect(Collectors.toList());
            log.info("📊 Фильтр по объёму торгов (мин. {} лот/день): {} → {} результатов",
                    criteria.getMinAvgDailyVolume(), beforeFilter, results.size());
        }

        // Сортируем по убыванию оценки (score)
        results.sort(Comparator.comparingDouble(BondAnalysisResult::getScore).reversed());

        log.info("✅ Итого результатов после всех фильтров: {}", results.size());
        return results;
    }

    /**
     * Анализирует свечи одной облигации и рассчитывает метрики
     * ✅ С расчётом среднедневного объёма торгов
     */
    private BondAnalysisResult analyzeCandles(Bond bond, List<HistoricCandle> candles) {
        BondAnalysisResult result = new BondAnalysisResult();

        // Базовые данные облигации
        result.setFigi(bond.getFigi());
        result.setTicker(bond.getTicker());
        result.setName(bond.getName());
        result.setNominalCurrency(bond.getNominal().getCurrency());

        if (bond.hasMaturityDate()) {
            long seconds = bond.getMaturityDate().getSeconds();
            result.setMaturityDate(LocalDate.ofEpochDay(seconds / 86400));
        }

        if (bond.hasDlong()) {
            double dlong = bond.getDlong().getUnits() + bond.getDlong().getNano() / 1e9;
            result.setDlong(dlong);
        }

        result.setRiskLevel(bond.getRiskLevel().name());

        // Извлекаем цены закрытия из свечей
        double[] closes = candles.stream()
                .mapToDouble(c -> c.getClose().getUnits() + c.getClose().getNano() / 1e9)
                .toArray();

        // 1. Волатильность (стандартное отклонение)
        double volatility = calculateVolatility(closes);
        result.setVolatility(volatility);

        // ✅ 1a. Среднедневной объём торгов (в лотах)
        double avgDailyVolume = candles.stream()
                .mapToLong(HistoricCandle::getVolume)  // Volume уже в лотах
                .average()
                .orElse(0);
        result.setAvgDailyVolume(avgDailyVolume);

        // 2. Средняя цена за период
        double avgPrice = Arrays.stream(closes).average().orElse(0);
        result.setAvgPrice(avgPrice);

        // 3. Текущая цена (последняя свеча)
        double currentPrice = closes[closes.length - 1];
        result.setCurrentPrice(currentPrice);

        // 4. Изменение цены от начала к концу периода (%)
        double priceChange = ((currentPrice - closes[0]) / closes[0]) * 100;
        result.setPriceChangePercent(priceChange);

        // 5. Максимум и минимум за период
        double maxPrice = Arrays.stream(closes).max().orElse(0);
        double minPrice = Arrays.stream(closes).min().orElse(0);
        result.setMaxPrice(maxPrice);
        result.setMinPrice(minPrice);

        // 6. Диапазон изменения цены (%)
        double priceRange = ((maxPrice - minPrice) / minPrice) * 100;
        result.setPriceRangePercent(priceRange);

        // 7. Тренд (линейная регрессия)
        double trend = calculateTrend(closes);
        result.setTrend(trend);

        // 8. Итоговая оценка (score)
        double score = calculateScore(volatility, priceChange, trend, bond);
        result.setScore(score);

        return result;
    }

    /**
     * Рассчитывает волатильность (стандартное отклонение цен)
     */
    private double calculateVolatility(double[] prices) {
        double mean = Arrays.stream(prices).average().orElse(0);
        double variance = Arrays.stream(prices)
                .map(p -> Math.pow(p - mean, 2))
                .average()
                .orElse(0);
        return Math.sqrt(variance);
    }

    /**
     * Рассчитывает тренд (наклон линейной регрессии)
     */
    private double calculateTrend(double[] prices) {
        int n = prices.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += prices[i];
            sumXY += i * prices[i];
            sumX2 += i * i;
        }

        // Наклон линии тренда (slope)
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return slope;
    }

    /**
     * Рассчитывает итоговую оценку облигации на основе метрик
     */
    private double calculateScore(double volatility, double priceChange, double trend, Bond bond) {
        double score = 0;

        // 1. Низкая волатильность — лучше (обратная зависимость)
        score += (1.0 / (volatility + 0.001)) * 10;

        // 2. Положительный тренд — лучше
        if (trend > 0) {
            score += trend * 100;
        }

        // 3. Наличие dlong — бонус
        if (bond.hasDlong()) {
            double dlongValue = bond.getDlong().getUnits() + bond.getDlong().getNano() / 1e9;
            if (dlongValue > 0) {
                score += 50;
            }
        }

        // 4. Низкий уровень риска — бонус
        if (bond.getRiskLevel() == RiskLevel.RISK_LEVEL_LOW) {
            score += 30;
        } else if (bond.getRiskLevel() == RiskLevel.RISK_LEVEL_MODERATE) {
            score += 15;
        }

        return score;
    }

    // ═══════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Критерии фильтрации облигаций
     * ✅ С поддержкой фильтра по минимальному объёму торгов
     */
    public static class BondsFilterCriteria {
        private String nominalCurrency = "RUB";
        private boolean withoutAmortization = true;
        private int minDaysToMaturity = DEFAULT_MIN_DAYS_TO_MATURITY;
        private int maxMonthsToMaturity = DEFAULT_MAX_MONTHS_TO_MATURITY;
        private boolean requireDlong = true;
        private boolean excludeHighRisk = true;
        private double minAvgDailyVolume = 0;  // ✅ НОВОЕ ПОЛЕ: Минимальный среднедневной объём торгов (лотов/день)

        // Getters and Setters

        public String getNominalCurrency() {
            return nominalCurrency;
        }

        public void setNominalCurrency(String nominalCurrency) {
            this.nominalCurrency = nominalCurrency;
        }

        public boolean isWithoutAmortization() {
            return withoutAmortization;
        }

        public void setWithoutAmortization(boolean withoutAmortization) {
            this.withoutAmortization = withoutAmortization;
        }

        public int getMinDaysToMaturity() {
            return minDaysToMaturity;
        }

        public void setMinDaysToMaturity(int minDaysToMaturity) {
            this.minDaysToMaturity = minDaysToMaturity;
        }

        public int getMaxMonthsToMaturity() {
            return maxMonthsToMaturity;
        }

        public void setMaxMonthsToMaturity(int maxMonthsToMaturity) {
            this.maxMonthsToMaturity = maxMonthsToMaturity;
        }

        public boolean isRequireDlong() {
            return requireDlong;
        }

        public void setRequireDlong(boolean requireDlong) {
            this.requireDlong = requireDlong;
        }

        public boolean isExcludeHighRisk() {
            return excludeHighRisk;
        }

        public void setExcludeHighRisk(boolean excludeHighRisk) {
            this.excludeHighRisk = excludeHighRisk;
        }

        // ✅ НОВЫЙ GETTER/SETTER
        public double getMinAvgDailyVolume() {
            return minAvgDailyVolume;
        }

        public void setMinAvgDailyVolume(double minAvgDailyVolume) {
            this.minAvgDailyVolume = minAvgDailyVolume;
        }

        @Override
        public String toString() {
            return String.format("Currency=%s, NoAmort=%b, Days=%d-%d months, Dlong=%b, ExcludeHighRisk=%b, MinVolume=%.0f",
                    nominalCurrency, withoutAmortization, minDaysToMaturity, maxMonthsToMaturity,
                    requireDlong, excludeHighRisk, minAvgDailyVolume);
        }
    }

    /**
     * Результат анализа одной облигации
     * ✅ С полем среднедневного объёма торгов
     */
    public static class BondAnalysisResult {
        private String figi;
        private String ticker;
        private String name;
        private String nominalCurrency;
        private LocalDate maturityDate;
        private double dlong;
        private String riskLevel;

        // Метрики на основе свечей
        private double volatility;
        private double avgDailyVolume;  // ✅ НОВОЕ ПОЛЕ: Среднедневной объём торгов (лотов)
        private double avgPrice;
        private double currentPrice;
        private double priceChangePercent;
        private double maxPrice;
        private double minPrice;
        private double priceRangePercent;
        private double trend;
        private double volatilityPercent;
        private double avgDailyRangePercent;
        private double score;

        // Getters and Setters

        public String getFigi() {
            return figi;
        }

        public void setFigi(String figi) {
            this.figi = figi;
        }

        public String getTicker() {
            return ticker;
        }

        public void setTicker(String ticker) {
            this.ticker = ticker;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNominalCurrency() {
            return nominalCurrency;
        }

        public void setNominalCurrency(String nominalCurrency) {
            this.nominalCurrency = nominalCurrency;
        }

        public LocalDate getMaturityDate() {
            return maturityDate;
        }

        public void setMaturityDate(LocalDate maturityDate) {
            this.maturityDate = maturityDate;
        }

        public double getDlong() {
            return dlong;
        }

        public void setDlong(double dlong) {
            this.dlong = dlong;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public double getVolatility() {
            return volatility;
        }

        public void setVolatility(double volatility) {
            this.volatility = volatility;
        }

        // ✅ НОВЫЙ GETTER/SETTER
        public double getAvgDailyVolume() {
            return avgDailyVolume;
        }

        public void setAvgDailyVolume(double avgDailyVolume) {
            this.avgDailyVolume = avgDailyVolume;
        }

        public double getAvgPrice() {
            return avgPrice;
        }

        public void setAvgPrice(double avgPrice) {
            this.avgPrice = avgPrice;
        }

        public double getCurrentPrice() {
            return currentPrice;
        }

        public void setCurrentPrice(double currentPrice) {
            this.currentPrice = currentPrice;
        }

        public double getPriceChangePercent() {
            return priceChangePercent;
        }

        public void setPriceChangePercent(double priceChangePercent) {
            this.priceChangePercent = priceChangePercent;
        }

        public double getMaxPrice() {
            return maxPrice;
        }

        public void setMaxPrice(double maxPrice) {
            this.maxPrice = maxPrice;
        }

        public double getMinPrice() {
            return minPrice;
        }

        public void setMinPrice(double minPrice) {
            this.minPrice = minPrice;
        }

        public double getPriceRangePercent() {
            return priceRangePercent;
        }

        public void setPriceRangePercent(double priceRangePercent) {
            this.priceRangePercent = priceRangePercent;
        }

        public double getTrend() {
            return trend;
        }

        public void setTrend(double trend) {
            this.trend = trend;
        }

        public double getVolatilityPercent() {
            return volatilityPercent;
        }

        public void setVolatilityPercent(double volatilityPercent) {
            this.volatilityPercent = volatilityPercent;
        }

        public double getAvgDailyRangePercent() {
            return avgDailyRangePercent;
        }

        public void setAvgDailyRangePercent(double avgDailyRangePercent) {
            this.avgDailyRangePercent = avgDailyRangePercent;
        }

        public double getScore() {
            // Приоритет: волатильность -> dlong -> объём

            double vol = Math.max(0.0, volatilityPercent);

            double dlongNorm = Math.min(Math.max(dlong, 0.0), 10.0);

            double volumeScore = Math.log1p(Math.max(0.0, avgDailyVolume));

            double wVol = 1.0;   // основной фактор
            double wDlong = 0.3; // второстепенный
            double wVolm = 0.2;  // третичный

            double baseScore = wVol * vol + wDlong * dlongNorm + wVolm * volumeScore;

            double trendPenalty = trend < -2.0 ? Math.min(Math.abs(trend) / 5.0, 5.0) : 0.0;

            this.score = Math.max(0.0, baseScore - trendPenalty);
            return this.score;
        }

        public void setScore(double score) {
            this.score = score;
        }
    }
}