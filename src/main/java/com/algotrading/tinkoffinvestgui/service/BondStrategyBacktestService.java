package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.api.CandlesApiService;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.repository.BondsRepository;
import com.algotrading.tinkoffinvestgui.repository.ParametersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для бэктестинга стратегии "ловец дна"
 * ✅ С учётом комиссии брокера из БД
 * ✅ Использует ту же логику что и BondStrategyCalculator
 * ✅ С фильтрацией по среднедневному объёму торгов
 * ✅ С выводом Dlong и прибыли без комиссии в отчёт
 */
public class BondStrategyBacktestService {

    private static final Logger log = LoggerFactory.getLogger(BondStrategyBacktestService.class);

    private final CandlesApiService candlesApiService;
    private final BondsRepository bondsRepository;
    private final ParametersRepository parametersRepository;

    // ✅ Параметры стратегии (те же что в BondStrategyCalculator)
    private static final double VOLATILITY_MULTIPLIER = 1.2; // k для расчёта цены покупки
    private static final double PROFIT_MARGIN = 0.008; // 0.8% прибыли при продаже

    public BondStrategyBacktestService(CandlesApiService candlesApiService,
                                       BondsRepository bondsRepository,
                                       ParametersRepository parametersRepository) {
        this.candlesApiService = candlesApiService;
        this.bondsRepository = bondsRepository;
        this.parametersRepository = parametersRepository;
    }

    /**
     * Запуск бэктестинга стратегии
     */
    public BacktestReport runBacktest(LocalDate startDate, LocalDate endDate, BacktestFilters filters) throws Exception {
        log.info("🧪 Начало бэктестинга: {} - {} с фильтрами: валюта={}, без_амортизации={}, мин_объём={}",
                startDate, endDate, filters.currency, filters.withoutAmortization, filters.minAvgDailyVolume);

        // ✅ Загрузить комиссию брокера из БД
        double brokerCommission = parametersRepository.getBrokerCommissionDecimal();
        log.info("📊 Используется комиссия брокера: {:.4f}%", brokerCommission * 100);

        // Получить период анализа из БД
        int analysisPeriodMonths = getAnalysisPeriodMonths();
        log.info("📊 Период анализа волатильности: {} месяцев", analysisPeriodMonths);

        // Получить облигации с фильтрами
        List<BondInfo> bonds = loadBondsWithFilters(filters);
        log.info("📈 Загружено {} облигаций для бэктеста", bonds.size());

        if (bonds.isEmpty()) {
            throw new Exception("Нет облигаций для бэктестинга. Проверьте фильтры.");
        }

        // ✅ ФИЛЬТРАЦИЯ ПО СРЕДНЕДНЕВНОМУ ОБЪЁМУ
        if (filters.minAvgDailyVolume > 0) {
            int beforeVolumeFilter = bonds.size();
            bonds = filterByAvgDailyVolume(bonds, filters.minAvgDailyVolume, analysisPeriodMonths);
            log.info("📊 Фильтр по объёму торгов (мин. {} лот/день): {} → {} облигаций",
                    filters.minAvgDailyVolume, beforeVolumeFilter, bonds.size());
        }

        // Для каждой облигации запустить бэктест
        List<BondBacktestResult> results = new ArrayList<>();
        int processed = 0;

        for (BondInfo bond : bonds) {
            processed++;
            if (processed % 10 == 0) {
                log.info("⏳ Обработано {}/{} облигаций ({}%)", processed, bonds.size(),
                        (processed * 100) / bonds.size());
            }

            try {
                // ✅ Передаём комиссию в бэктест
                BondBacktestResult result = backtestBond(bond, startDate, endDate,
                        analysisPeriodMonths, brokerCommission);

                if (result.getTotalTrades() > 0) {
                    results.add(result);
                }
            } catch (Exception e) {
                log.warn("⚠️ Ошибка бэктеста для {}: {}", bond.ticker, e.getMessage());
            }
        }

        // Сформировать общий отчёт
        BacktestReport report = new BacktestReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setAnalysisPeriodMonths(analysisPeriodMonths);
        report.setBondResults(results);

        // Агрегированная статистика
        int totalTrades = results.stream().mapToInt(BondBacktestResult::getTotalTrades).sum();
        int profitableTrades = results.stream().mapToInt(BondBacktestResult::getProfitableTrades).sum();
        int losingTrades = results.stream().mapToInt(BondBacktestResult::getLosingTrades).sum();
        double totalProfit = results.stream().mapToDouble(BondBacktestResult::getTotalProfit).sum();
        double avgProfitPerTrade = totalTrades > 0 ? totalProfit / totalTrades : 0;
        double winRate = totalTrades > 0 ? (profitableTrades * 100.0 / totalTrades) : 0;

        double avgHoldingDays = results.stream()
                .flatMap(r -> r.getTrades().stream())
                .mapToInt(Trade::getHoldingDays)
                .average()
                .orElse(0);

        double avgProfitPercent = results.stream()
                .flatMap(r -> r.getTrades().stream())
                .mapToDouble(Trade::getProfitPercent)
                .average()
                .orElse(0);

        report.setTotalBonds(results.size());
        report.setTotalTrades(totalTrades);
        report.setProfitableTrades(profitableTrades);
        report.setLosingTrades(losingTrades);
        report.setTotalProfit(totalProfit);
        report.setAvgProfitPerTrade(avgProfitPerTrade);
        report.setAvgProfitPercent(avgProfitPercent);
        report.setWinRate(winRate);
        report.setAvgHoldingDays(avgHoldingDays);

        log.info("✅ Бэктест завершён: {} облигаций, {} сделок, винрейт {:.1f}%, общая прибыль {:.2f}₽",
                results.size(), totalTrades, winRate, totalProfit);

        return report;
    }

    /**
     * ✅ НОВОЕ: Фильтрация по среднедневному объёму торгов
     */
    private List<BondInfo> filterByAvgDailyVolume(List<BondInfo> bonds, double minVolume, int analysisPeriodMonths) {
        List<BondInfo> filtered = new ArrayList<>();
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusMonths(analysisPeriodMonths);

        for (BondInfo bond : bonds) {
            try {
                List<HistoricCandle> candles = candlesApiService.getCandles(
                        bond.figi, startDate, endDate, CandleInterval.CANDLE_INTERVAL_DAY);

                if (!candles.isEmpty()) {
                    double avgVolume = candles.stream()
                            .mapToDouble(c -> c.getVolume())
                            .average()
                            .orElse(0);

                    if (avgVolume >= minVolume) {
                        bond.avgDailyVolume = avgVolume;
                        filtered.add(bond);
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ Ошибка загрузки свечей для {}: {}", bond.ticker, e.getMessage());
            }
        }

        return filtered;
    }

    /**
     * Бэктестинг для одной облигации
     * ✅ С использованием той же логики что в BondStrategyCalculator
     */
    private BondBacktestResult backtestBond(BondInfo bond, LocalDate startDate, LocalDate endDate,
                                            int analysisPeriodMonths, double brokerCommission) throws Exception {
        // Загрузить исторические данные
        LocalDate dataStart = startDate.minusMonths(analysisPeriodMonths);
        List<HistoricCandle> allCandles = candlesApiService.getCandles(
                bond.figi, dataStart, endDate, CandleInterval.CANDLE_INTERVAL_DAY);

        if (allCandles.isEmpty()) {
            log.warn("⚠️ Нет данных для {}", bond.ticker);
            return createEmptyResult(bond);
        }

        List<Trade> trades = new ArrayList<>();
        Trade currentTrade = null;

        // Симуляция торговли по дням
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalDate finalDate = date;

            // Получить свечи за период анализа
            LocalDate analysisStart = date.minusMonths(analysisPeriodMonths);
            List<HistoricCandle> analysisPeriod = allCandles.stream()
                    .filter(c -> {
                        LocalDate candleDate = LocalDate.ofEpochDay(c.getTime().getSeconds() / 86400);
                        return !candleDate.isBefore(analysisStart) && !candleDate.isAfter(finalDate);
                    })
                    .collect(Collectors.toList());

            if (analysisPeriod.isEmpty()) {
                continue;
            }

            // Рассчитать волатильность и цены
            double[] prices = analysisPeriod.stream()
                    .mapToDouble(c -> c.getClose().getUnits() + c.getClose().getNano() / 1e9)
                    .toArray();

            double volatility = calculateVolatility(prices);
            double avgPrice = calculateAverage(prices);
            double lastPrice = prices[prices.length - 1]; // Вчерашняя цена

            // ✅ ЛОГИКА ИЗ BondStrategyCalculator:
            // buyPrice = lastPrice - 1.2 × volatility
            double buyPrice = lastPrice - (VOLATILITY_MULTIPLIER * volatility);

            // ✅ КОМИССИЯ ПРИ ПОКУПКЕ
            double buyCommission = buyPrice * brokerCommission;

            // ✅ ЦЕНА ПРОДАЖИ С УЧЁТОМ КОМИССИЙ И ПРИБЫЛИ
            double targetProfit = buyPrice * PROFIT_MARGIN; // 0.8% прибыли
            double sellCommission = (buyPrice + targetProfit) * brokerCommission;
            double sellPrice = buyPrice + buyCommission + targetProfit + sellCommission;

            // Если нет позиции и текущая цена упала до buyPrice или ниже
            if (currentTrade == null && lastPrice <= buyPrice) {
                currentTrade = new Trade();
                currentTrade.setBuyDate(date);
                currentTrade.setBuyPrice(buyPrice);
                currentTrade.setVolatility(volatility);
                currentTrade.setBuyCommission(buyCommission);
                currentTrade.setTargetSellPrice(sellPrice);

                log.debug("📥 {} Покупка по {:.2f}₽ (таргет {:.2f}₽, комиссия {:.2f}₽)",
                        bond.ticker, buyPrice, sellPrice, buyCommission);
            }

            // Если есть позиция - проверяем условия продажи
            if (currentTrade != null) {
                int holdingDays = (int) (date.toEpochDay() - currentTrade.getBuyDate().toEpochDay());

                // ✅ Продажа если:
                // 1. Цена достигла таргета (sellPrice)
                // 2. Прошло 30 дней (выход по таймауту)
                boolean reachedTarget = lastPrice >= currentTrade.getTargetSellPrice();
                boolean timeout = holdingDays >= 30;

                if (reachedTarget || timeout) {
                    double actualSellPrice = lastPrice;
                    double actualSellCommission = actualSellPrice * brokerCommission;

                    // ✅ ПРИБЫЛЬ БЕЗ КОМИССИИ
                    double profitBeforeCommission = actualSellPrice - currentTrade.getBuyPrice();

                    // ✅ ЧИСТАЯ ПРИБЫЛЬ с учётом ВСЕХ комиссий
                    double totalCommissions = currentTrade.getBuyCommission() + actualSellCommission;
                    double netProfit = profitBeforeCommission - totalCommissions;
                    double profitPercent = (netProfit / currentTrade.getBuyPrice()) * 100;

                    currentTrade.setSellDate(date);
                    currentTrade.setSellPrice(actualSellPrice);
                    currentTrade.setSellCommission(actualSellCommission);
                    currentTrade.setHoldingDays(holdingDays);
                    currentTrade.setProfitBeforeCommission(profitBeforeCommission); // ✅ НОВОЕ
                    currentTrade.setProfit(netProfit);
                    currentTrade.setProfitPercent(profitPercent);

                    trades.add(currentTrade);

                    String reason = reachedTarget ? "таргет" : "таймаут";
                    log.debug("📤 {} Продажа по {:.2f}₽ (прибыль БЕЗ комиссии {:.2f}₽, чистая {:.2f}₽/{:.2f}%, комиссии {:.2f}₽, причина: {})",
                            bond.ticker, actualSellPrice, profitBeforeCommission, netProfit, profitPercent, totalCommissions, reason);

                    currentTrade = null;
                }
            }
        }

        // Закрыть открытую позицию в конце периода
        if (currentTrade != null) {
            double[] lastPrices = allCandles.stream()
                    .mapToDouble(c -> c.getClose().getUnits() + c.getClose().getNano() / 1e9)
                    .toArray();

            double lastPrice = lastPrices[lastPrices.length - 1];
            int holdingDays = (int) (endDate.toEpochDay() - currentTrade.getBuyDate().toEpochDay());
            double actualSellCommission = lastPrice * brokerCommission;

            double profitBeforeCommission = lastPrice - currentTrade.getBuyPrice();
            double totalCommissions = currentTrade.getBuyCommission() + actualSellCommission;
            double netProfit = profitBeforeCommission - totalCommissions;
            double profitPercent = (netProfit / currentTrade.getBuyPrice()) * 100;

            currentTrade.setSellDate(endDate);
            currentTrade.setSellPrice(lastPrice);
            currentTrade.setSellCommission(actualSellCommission);
            currentTrade.setHoldingDays(holdingDays);
            currentTrade.setProfitBeforeCommission(profitBeforeCommission); // ✅ НОВОЕ
            currentTrade.setProfit(netProfit);
            currentTrade.setProfitPercent(profitPercent);
            trades.add(currentTrade);

            log.debug("📤 {} Закрытие позиции в конце периода по {:.2f}₽ (прибыль БЕЗ комиссии {:.2f}₽, чистая {:.2f}₽/{:.2f}%)",
                    bond.ticker, lastPrice, profitBeforeCommission, netProfit, profitPercent);
        }

        // Сформировать результат
        BondBacktestResult result = new BondBacktestResult();
        result.setTicker(bond.ticker);
        result.setName(bond.name);
        result.setFigi(bond.figi);
        result.setDlong(bond.dlong); // ✅ НОВОЕ: Dlong
        result.setAvgDailyVolume(bond.avgDailyVolume); // ✅ НОВОЕ: Объём
        result.setTrades(trades);

        int profitable = (int) trades.stream().filter(t -> t.getProfit() > 0).count();
        int losing = trades.size() - profitable;
        double totalProfit = trades.stream().mapToDouble(Trade::getProfit).sum();
        double totalProfitBeforeCommission = trades.stream().mapToDouble(Trade::getProfitBeforeCommission).sum(); // ✅ НОВОЕ
        double avgProfit = trades.isEmpty() ? 0 : totalProfit / trades.size();
        double avgProfitBeforeCommission = trades.isEmpty() ? 0 : totalProfitBeforeCommission / trades.size(); // ✅ НОВОЕ
        double avgProfitPercent = trades.stream().mapToDouble(Trade::getProfitPercent).average().orElse(0);
        double winRate = trades.isEmpty() ? 0 : (profitable * 100.0 / trades.size());
        double avgHolding = trades.stream().mapToInt(Trade::getHoldingDays).average().orElse(0);

        result.setTotalTrades(trades.size());
        result.setProfitableTrades(profitable);
        result.setLosingTrades(losing);
        result.setTotalProfit(totalProfit);
        result.setTotalProfitBeforeCommission(totalProfitBeforeCommission); // ✅ НОВОЕ
        result.setAvgProfit(avgProfit);
        result.setAvgProfitBeforeCommission(avgProfitBeforeCommission); // ✅ НОВОЕ
        result.setAvgProfitPercent(avgProfitPercent);
        result.setWinRate(winRate);
        result.setAvgHoldingDays(avgHolding);

        return result;
    }

    /**
     * Получить период анализа из БД
     */
    private int getAnalysisPeriodMonths() {
        try {
            String value = parametersRepository.getParameter("analysis_period_months");
            if (value != null && !value.trim().isEmpty()) {
                return Integer.parseInt(value.trim());
            }

            log.warn("⚠️ Параметр analysis_period_months не найден в БД, используем 4 по умолчанию");
            return 4;
        } catch (NumberFormatException e) {
            log.error("❌ Неверный формат analysis_period_months: {}", e.getMessage());
            return 4;
        } catch (Exception e) {
            log.error("❌ Ошибка чтения analysis_period_months из БД", e);
            return 4;
        }
    }

    /**
     * Загрузить облигации с фильтрами
     */
    private List<BondInfo> loadBondsWithFilters(BacktestFilters filters) throws Exception {
        List<BondInfo> bonds = new ArrayList<>();
        LocalDate now = LocalDate.now();
        LocalDate minMaturityDate = now.plusDays(filters.minDaysToMaturity);
        LocalDate maxMaturityDate = now.plusMonths(filters.maxMonthsToMaturity);
        long minMaturitySeconds = minMaturityDate.toEpochDay() * 86400;
        long maxMaturitySeconds = maxMaturityDate.toEpochDay() * 86400;

        // Строим динамический SQL в зависимости от фильтров
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT field01 as figi, field02 as ticker, field05 as name, ")
                .append("       field10 as dlong_str ")
                .append("FROM public.exportdata ")
                .append("WHERE field01 != 'figi' ");

        // 1. Валюта
        if (filters.currency != null && !filters.currency.isEmpty()) {
            sqlBuilder.append(" AND UPPER(field07) = '").append(filters.currency.toUpperCase()).append("' ");
        }

        // 2. Без амортизации
        if (filters.withoutAmortization) {
            sqlBuilder.append(" AND field12 = 'false' ");
        }

        // 3. Срок погашения
        sqlBuilder.append(" AND (field09 IS NULL OR field09 = '' OR ")
                .append("      (field09 ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' AND ")
                .append("       EXTRACT(EPOCH FROM field09::date) BETWEEN ")
                .append(minMaturitySeconds).append(" AND ").append(maxMaturitySeconds).append(")) ");

        // 4. Dlong
        if (filters.requireDlong) {
            sqlBuilder.append(" AND field10 IS NOT NULL AND field10 != '' ");
        }

        // 5. Исключить высокий риск
        if (filters.excludeHighRisk) {
            sqlBuilder.append(" AND UPPER(field13) != 'HIGH'");
        }

        String sql = sqlBuilder.toString();
        String dbUrl = ConnectorConfig.getPropertyValue("db.url");
        String dbUser = ConnectorConfig.getPropertyValue("db.username");
        String dbPassword = ConnectorConfig.getPropertyValue("db.password");

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    String dlongStr = rs.getString("dlong_str");
                    if (dlongStr != null && !dlongStr.trim().isEmpty()) {
                        // Заменяем запятую на точку в Java
                        double dlong = Double.parseDouble(dlongStr.replace(',', '.'));

                        if (!filters.requireDlong || dlong > 0) {
                            BondInfo bond = new BondInfo();
                            bond.figi = rs.getString("figi");
                            bond.ticker = rs.getString("ticker");
                            bond.name = rs.getString("name");
                            bond.dlong = dlong;
                            bonds.add(bond);
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("⚠️ Не удалось распарсить dlong для {}: {}",
                            rs.getString("ticker"), e.getMessage());
                }
            }
        }

        log.info("📊 Загружено {} облигаций с фильтрами: валюта={}, без_амортизации={}, срок={}д-{}м, Dlong={}, риск={}",
                bonds.size(), filters.currency, filters.withoutAmortization ? "да" : "нет",
                filters.minDaysToMaturity, filters.maxMonthsToMaturity,
                filters.requireDlong ? ">0" : "любой", filters.excludeHighRisk ? "низкий/средний" : "любой");

        return bonds;
    }

    /**
     * Расчёт волатильности (стандартное отклонение)
     */
    private double calculateVolatility(double[] prices) {
        double mean = calculateAverage(prices);
        double variance = 0;
        for (double price : prices) {
            variance += Math.pow(price - mean, 2);
        }
        variance /= prices.length;
        return Math.sqrt(variance);
    }

    /**
     * Расчёт среднего
     */
    private double calculateAverage(double[] prices) {
        double sum = 0;
        for (double price : prices) {
            sum += price;
        }
        return sum / prices.length;
    }

    /**
     * Создать пустой результат
     */
    private BondBacktestResult createEmptyResult(BondInfo bond) {
        BondBacktestResult result = new BondBacktestResult();
        result.setTicker(bond.ticker);
        result.setName(bond.name);
        result.setFigi(bond.figi);
        result.setDlong(bond.dlong);
        result.setAvgDailyVolume(bond.avgDailyVolume);
        result.setTrades(new ArrayList<>());
        return result;
    }

    // ========== КЛАССЫ ДАННЫХ ==========

    /**
     * Информация об облигации
     */
    public static class BondInfo {
        public String figi;
        public String ticker;
        public String name;
        public double dlong;
        public double avgDailyVolume = 0; // ✅ НОВОЕ: среднедневной объём
    }

    /**
     * Параметры фильтрации облигаций для бэктеста
     */
    public static class BacktestFilters {
        public String currency = "RUB";
        public boolean withoutAmortization = true;
        public int minDaysToMaturity = 3;
        public int maxMonthsToMaturity = 15;
        public boolean requireDlong = true;
        public boolean excludeHighRisk = true;
        public double minAvgDailyVolume = 0; // ✅ НОВОЕ: минимальный среднедневной объём (лотов/день)
    }

    /**
     * Одна сделка
     */
    public static class Trade {
        private LocalDate buyDate;
        private double buyPrice;
        private double volatility;
        private double buyCommission;
        private double targetSellPrice;
        private LocalDate sellDate;
        private double sellPrice;
        private double sellCommission;
        private int holdingDays;
        private double profitBeforeCommission; // ✅ НОВОЕ: прибыль БЕЗ комиссии
        private double profit; // Чистая прибыль после комиссий
        private double profitPercent;

        // Getters and Setters
        public LocalDate getBuyDate() { return buyDate; }
        public void setBuyDate(LocalDate buyDate) { this.buyDate = buyDate; }

        public double getBuyPrice() { return buyPrice; }
        public void setBuyPrice(double buyPrice) { this.buyPrice = buyPrice; }

        public double getVolatility() { return volatility; }
        public void setVolatility(double volatility) { this.volatility = volatility; }

        public double getBuyCommission() { return buyCommission; }
        public void setBuyCommission(double buyCommission) { this.buyCommission = buyCommission; }

        public double getTargetSellPrice() { return targetSellPrice; }
        public void setTargetSellPrice(double targetSellPrice) { this.targetSellPrice = targetSellPrice; }

        public LocalDate getSellDate() { return sellDate; }
        public void setSellDate(LocalDate sellDate) { this.sellDate = sellDate; }

        public double getSellPrice() { return sellPrice; }
        public void setSellPrice(double sellPrice) { this.sellPrice = sellPrice; }

        public double getSellCommission() { return sellCommission; }
        public void setSellCommission(double sellCommission) { this.sellCommission = sellCommission; }

        public int getHoldingDays() { return holdingDays; }
        public void setHoldingDays(int holdingDays) { this.holdingDays = holdingDays; }

        public double getProfitBeforeCommission() { return profitBeforeCommission; }
        public void setProfitBeforeCommission(double profitBeforeCommission) { this.profitBeforeCommission = profitBeforeCommission; }

        public double getProfit() { return profit; }
        public void setProfit(double profit) { this.profit = profit; }

        public double getProfitPercent() { return profitPercent; }
        public void setProfitPercent(double profitPercent) { this.profitPercent = profitPercent; }
    }

    /**
     * Результат бэктестинга для одной облигации
     */
    public static class BondBacktestResult {
        private String ticker;
        private String name;
        private String figi;
        private double dlong; // ✅ НОВОЕ
        private double avgDailyVolume; // ✅ НОВОЕ
        private List<Trade> trades;
        private int totalTrades;
        private int profitableTrades;
        private int losingTrades;
        private double totalProfitBeforeCommission; // ✅ НОВОЕ
        private double totalProfit;
        private double avgProfitBeforeCommission; // ✅ НОВОЕ
        private double avgProfit;
        private double avgProfitPercent;
        private double winRate;
        private double avgHoldingDays;

        // Getters and Setters
        public String getTicker() { return ticker; }
        public void setTicker(String ticker) { this.ticker = ticker; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getFigi() {return figi;}
        public void setFigi(String figi) {this.figi = figi;}

        public double getDlong() { return dlong; }
        public void setDlong(double dlong) { this.dlong = dlong; }

        public double getAvgDailyVolume() { return avgDailyVolume; }
        public void setAvgDailyVolume(double avgDailyVolume) { this.avgDailyVolume = avgDailyVolume; }

        public List<Trade> getTrades() { return trades; }
        public void setTrades(List<Trade> trades) { this.trades = trades; }

        public int getTotalTrades() { return totalTrades; }
        public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }

        public int getProfitableTrades() { return profitableTrades; }
        public void setProfitableTrades(int profitableTrades) { this.profitableTrades = profitableTrades; }

        public int getLosingTrades() { return losingTrades; }
        public void setLosingTrades(int losingTrades) { this.losingTrades = losingTrades; }

        public double getTotalProfitBeforeCommission() { return totalProfitBeforeCommission; }
        public void setTotalProfitBeforeCommission(double totalProfitBeforeCommission) { this.totalProfitBeforeCommission = totalProfitBeforeCommission; }

        public double getTotalProfit() { return totalProfit; }
        public void setTotalProfit(double totalProfit) { this.totalProfit = totalProfit; }

        public double getAvgProfitBeforeCommission() { return avgProfitBeforeCommission; }
        public void setAvgProfitBeforeCommission(double avgProfitBeforeCommission) { this.avgProfitBeforeCommission = avgProfitBeforeCommission; }

        public double getAvgProfit() { return avgProfit; }
        public void setAvgProfit(double avgProfit) { this.avgProfit = avgProfit; }

        public double getAvgProfitPercent() { return avgProfitPercent; }
        public void setAvgProfitPercent(double avgProfitPercent) { this.avgProfitPercent = avgProfitPercent; }

        public double getWinRate() { return winRate; }
        public void setWinRate(double winRate) { this.winRate = winRate; }

        public double getAvgHoldingDays() { return avgHoldingDays; }
        public void setAvgHoldingDays(double avgHoldingDays) { this.avgHoldingDays = avgHoldingDays; }
    }

    /**
     * Общий отчёт о бэктестинге
     */
    public static class BacktestReport {
        private LocalDate startDate;
        private LocalDate endDate;
        private int analysisPeriodMonths;
        private int totalBonds;
        private List<BondBacktestResult> bondResults;
        private int totalTrades;
        private int profitableTrades;
        private int losingTrades;
        private double totalProfit;
        private double avgProfitPerTrade;
        private double avgProfitPercent;
        private double winRate;
        private double avgHoldingDays;

        // Getters and Setters
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

        public int getAnalysisPeriodMonths() { return analysisPeriodMonths; }
        public void setAnalysisPeriodMonths(int analysisPeriodMonths) { this.analysisPeriodMonths = analysisPeriodMonths; }

        public int getTotalBonds() { return totalBonds; }
        public void setTotalBonds(int totalBonds) { this.totalBonds = totalBonds; }

        public List<BondBacktestResult> getBondResults() { return bondResults; }
        public void setBondResults(List<BondBacktestResult> bondResults) { this.bondResults = bondResults; }

        public int getTotalTrades() { return totalTrades; }
        public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }

        public int getProfitableTrades() { return profitableTrades; }
        public void setProfitableTrades(int profitableTrades) { this.profitableTrades = profitableTrades; }

        public int getLosingTrades() { return losingTrades; }
        public void setLosingTrades(int losingTrades) { this.losingTrades = losingTrades; }

        public double getTotalProfit() { return totalProfit; }
        public void setTotalProfit(double totalProfit) { this.totalProfit = totalProfit; }

        public double getAvgProfitPerTrade() { return avgProfitPerTrade; }
        public void setAvgProfitPerTrade(double avgProfitPerTrade) { this.avgProfitPerTrade = avgProfitPerTrade; }

        public double getAvgProfitPercent() { return avgProfitPercent; }
        public void setAvgProfitPercent(double avgProfitPercent) { this.avgProfitPercent = avgProfitPercent; }

        public double getWinRate() { return winRate; }
        public void setWinRate(double winRate) { this.winRate = winRate; }

        public double getAvgHoldingDays() { return avgHoldingDays; }
        public void setAvgHoldingDays(double avgHoldingDays) { this.avgHoldingDays = avgHoldingDays; }
    }
}
