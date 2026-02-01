package com.algotrading.tinkoffinvestgui.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.algotrading.tinkoffinvestgui.repository.ParametersRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Калькулятор стратегии "Ловец дна" с адаптивной маржой
 * ✅ С отображением прибыли с комиссиями и без для прозрачности
 */
public class BondStrategyCalculator {

    private static final Logger log = LoggerFactory.getLogger(BondStrategyCalculator.class);

    // ✅ PRICE_BASE захардкожена как LAST_PRICE
    private static final String PRICE_BASE = "LAST_PRICE";

    /**
     * Рассчитывает рекомендованные цены с адаптивной маржой на основе волатильности
     *
     * @param analysis результат анализа облигации
     * @param params   параметры стратегии из БД
     * @return рекомендация по стратегии
     */
    public static StrategyRecommendation calculatePrices(
            BondsAnalysisService.BondAnalysisResult analysis,
            ParametersRepository.StrategyParameters params) {

        double lastPrice = analysis.getCurrentPrice();
        double volatility = analysis.getVolatility();
        double avgPrice = analysis.getAvgPrice();
        double volatilityPercent = (volatility / avgPrice) * 100;

        // ✅ ЦЕНА ПОКУПКИ (LAST_PRICE захардкожена)
        double buyPrice = lastPrice - (params.volatilityMultiplier * volatility);

        log.debug("📊 {} Расчёт от LAST_PRICE: {} - ({} × {}) = {}",
                analysis.getTicker(), lastPrice, params.volatilityMultiplier,
                volatility, buyPrice);

        // ✅ КОМИССИЯ ПРИ ПОКУПКЕ
        double buyCommission = buyPrice * params.brokerCommission;

        // ✅ АДАПТИВНАЯ МАРЖА на основе волатильности
        // Формула: profitMarginPercent = volatilityCoefficient × volatilityPercent
        // Ограничена диапазоном [minProfitMarginPercent, maxProfitMarginPercent]
        double rawProfitMarginPercent = params.volatilityCoefficient * volatilityPercent;
        double profitMarginPercent = Math.max(params.minProfitMarginPercent,
                Math.min(params.maxProfitMarginPercent, rawProfitMarginPercent));
        double profitMargin = profitMarginPercent / 100.0; // Перевод в десятичное (0.8% → 0.008)

        log.debug("📊 {} Адаптивная маржа: коэфф={} × волатильность={:.2f}% = {:.2f}% " +
                        "(лимиты [{:.2f}%, {:.2f}%]) → итого {:.2f}%",
                analysis.getTicker(),
                params.volatilityCoefficient,
                volatilityPercent,
                rawProfitMarginPercent,
                params.minProfitMarginPercent,
                params.maxProfitMarginPercent,
                profitMarginPercent);

        // ✅ ЦЕНА ПРОДАЖИ с адаптивной маржой
        double targetProfit = buyPrice * profitMargin;
        double sellCommission = (buyPrice + targetProfit) * params.brokerCommission;
        double sellPrice = buyPrice + buyCommission + targetProfit + sellCommission;

        // Скидка от текущей цены (%)
        double discountPercent = ((lastPrice - buyPrice) / lastPrice) * 100;

        // ✅ ПРИБЫЛЬ БЕЗ КОМИССИЙ (теоретическая)
        double profitWithoutCommission = sellPrice - buyPrice;

        // ✅ ПРИБЫЛЬ С КОМИССИЯМИ (реальная)
        double totalCommissions = buyCommission + sellCommission;
        double netProfit = (sellPrice - buyPrice) - totalCommissions;
        double profitPercent = (netProfit / buyPrice) * 100;

        // Создать рекомендацию
        StrategyRecommendation rec = new StrategyRecommendation();
        rec.setCurrentPrice(BigDecimal.valueOf(lastPrice).setScale(2, RoundingMode.HALF_UP));
        rec.setVolatilityPercent(volatilityPercent);
        rec.setBuyPrice(BigDecimal.valueOf(buyPrice).setScale(2, RoundingMode.HALF_UP));
        rec.setSellPrice(BigDecimal.valueOf(sellPrice).setScale(2, RoundingMode.HALF_UP));
        rec.setDiscountPercent(discountPercent);
        rec.setProfitPercent(profitPercent);

        // ✅ Комиссии
        rec.setBuyCommission(buyCommission);
        rec.setSellCommission(sellCommission);
        rec.setTotalCommissions(totalCommissions);

        // ✅ Прибыль
        rec.setProfitWithoutCommission(profitWithoutCommission);  // БЕЗ комиссий
        rec.setNetProfit(netProfit);                               // С комиссиями

        // ✅ Адаптивная маржа
        rec.setAdaptiveMarginPercent(profitMarginPercent);

        // Рекомендация
        String recommendation = buildRecommendation(volatilityPercent, analysis, rec, params, profitMarginPercent);
        rec.setRecommendation(recommendation);

        log.debug("💡 {} Стратегия: купить {}₽, продать {}₽, " +
                        "адаптивная маржа {:.2f}%, профит БЕЗ ком. {:.2f}₽, профит С ком. {:.2f}₽",
                analysis.getTicker(), buyPrice, sellPrice,
                profitMarginPercent, profitWithoutCommission, netProfit);

        return rec;
    }

    /**
     * Формирует текстовую рекомендацию с адаптивной маржой
     */
    private static String buildRecommendation(double volatilityPercent,
                                              BondsAnalysisService.BondAnalysisResult analysis,
                                              StrategyRecommendation rec,
                                              ParametersRepository.StrategyParameters params,
                                              double adaptiveMarginPercent) {
        StringBuilder sb = new StringBuilder();

        // Оценка волатильности
        if (volatilityPercent > 2.0) {
            sb.append("⚠️ Высокая волатильность (>2%) - риск выше среднего\n");
        } else if (volatilityPercent > 1.0) {
            sb.append("✅ Умеренная волатильность (1-2%) - приемлемый риск\n");
        } else {
            sb.append("✅ Низкая волатильность (<1%) - низкий риск\n");
        }

        // Оценка тренда
        if (analysis.getTrend() > 0) {
            sb.append("📈 Тренд: восходящий - хорошо для покупки\n");
        } else if (analysis.getTrend() > -0.1) {
            sb.append("📊 Тренд: нейтральный - можно рассмотреть\n");
        } else {
            sb.append("📉 Тренд: нисходящий - осторожно!\n");
        }

        // Оценка Dlong
        if (analysis.getDlong() > 0) {
            sb.append(String.format("⏳ Дюрация: %.2f лет - корректная\n", analysis.getDlong()));
        } else {
            sb.append("⏳ Дюрация: нет данных - проверьте вручную\n");
        }

        // ✅ Информация об адаптивной марже
        sb.append(String.format("\n📊 Адаптивная маржа: %.2f%%\n", adaptiveMarginPercent));
        sb.append(String.format(" • Волатильность облигации: %.2f%%\n", volatilityPercent));
        sb.append(String.format(" • Коэффициент: %.2f\n", params.volatilityCoefficient));
        sb.append(String.format(" • Диапазон: %.2f%% - %.2f%%\n",
                params.minProfitMarginPercent, params.maxProfitMarginPercent));

        // ✅ Информация о прибыли (С и БЕЗ комиссий)
        sb.append("\n💰 Прибыль:\n");
        sb.append(String.format(" • БЕЗ комиссий: %.2f₽ (%.2f%%)\n",
                rec.getProfitWithoutCommission(),
                (rec.getProfitWithoutCommission() / rec.getBuyPrice().doubleValue()) * 100));
        sb.append(String.format(" • С комиссиями: %.2f₽ (%.2f%%)\n",
                rec.getNetProfit(), rec.getProfitPercent()));
        sb.append(String.format(" • Влияние комиссий: %.2f₽ (%.1f%% от прибыли)\n",
                rec.getTotalCommissions(),
                (rec.getTotalCommissions() / rec.getProfitWithoutCommission()) * 100));

        // Информация о комиссиях
        double commissionPercent = params.brokerCommission * 100;
        sb.append(String.format("\n💸 Комиссии брокера (%.2f%%):\n", commissionPercent));
        sb.append(String.format(" • При покупке: %.2f₽\n", rec.getBuyCommission()));
        sb.append(String.format(" • При продаже: %.2f₽\n", rec.getSellCommission()));
        sb.append(String.format(" • Всего комиссий: %.2f₽\n", rec.getTotalCommissions()));

        // Итоговая оценка
        double score = analysis.getScore();
        sb.append("\n🎯 Итоговая оценка:\n");
        if (score > 80) {
            sb.append(String.format("✅ Отличная возможность (оценка: %.0f/100)\n", score));
        } else if (score > 50) {
            sb.append(String.format("✅ Хорошая возможность (оценка: %.0f/100)\n", score));
        } else if (score > 30) {
            sb.append(String.format("⚠️ Средняя возможность (оценка: %.0f/100)\n", score));
        } else {
            sb.append(String.format("❌ Низкая оценка (оценка: %.0f/100) - не рекомендуется\n", score));
        }

        return sb.toString();
    }

    /**
     * @deprecated Используйте calculatePrices(analysis, params)
     * Оставлено для обратной совместимости
     */
    @Deprecated
    public static StrategyRecommendation calculatePrices(
            BondsAnalysisService.BondAnalysisResult analysis,
            double brokerCommission) {

        log.warn("⚠️ Используется устаревший метод calculatePrices без параметров стратегии. " +
                "Рекомендуется использовать версию с StrategyParameters.");

        // Создать параметры по умолчанию
        ParametersRepository.StrategyParameters params =
                new ParametersRepository.StrategyParameters();
        params.brokerCommission = brokerCommission;
        params.volatilityMultiplier = 1.2;
        params.volatilityCoefficient = 0.4;
        params.minProfitMarginPercent = 0.3;
        params.maxProfitMarginPercent = 2.0;

        return calculatePrices(analysis, params);
    }

    /**
     * DTO для рекомендации по стратегии
     */
    public static class StrategyRecommendation {
        private BigDecimal currentPrice;
        private double volatilityPercent;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private double discountPercent;
        private double profitPercent;
        private String recommendation;

        // Комиссии
        private double buyCommission;
        private double sellCommission;
        private double totalCommissions;

        // Прибыль
        private double profitWithoutCommission;  // ✅ НОВОЕ ПОЛЕ: Прибыль БЕЗ комиссий
        private double netProfit;                 // Прибыль С комиссиями

        // Адаптивная маржа
        private double adaptiveMarginPercent;

        // Getters and Setters

        public BigDecimal getCurrentPrice() {
            return currentPrice;
        }

        public void setCurrentPrice(BigDecimal currentPrice) {
            this.currentPrice = currentPrice;
        }

        public double getVolatilityPercent() {
            return volatilityPercent;
        }

        public void setVolatilityPercent(double volatilityPercent) {
            this.volatilityPercent = volatilityPercent;
        }

        public BigDecimal getBuyPrice() {
            return buyPrice;
        }

        public void setBuyPrice(BigDecimal buyPrice) {
            this.buyPrice = buyPrice;
        }

        public BigDecimal getSellPrice() {
            return sellPrice;
        }

        public void setSellPrice(BigDecimal sellPrice) {
            this.sellPrice = sellPrice;
        }

        public double getDiscountPercent() {
            return discountPercent;
        }

        public void setDiscountPercent(double discountPercent) {
            this.discountPercent = discountPercent;
        }

        public double getProfitPercent() {
            return profitPercent;
        }

        public void setProfitPercent(double profitPercent) {
            this.profitPercent = profitPercent;
        }

        public String getRecommendation() {
            return recommendation;
        }

        public void setRecommendation(String recommendation) {
            this.recommendation = recommendation;
        }

        public double getBuyCommission() {
            return buyCommission;
        }

        public void setBuyCommission(double buyCommission) {
            this.buyCommission = buyCommission;
        }

        public double getSellCommission() {
            return sellCommission;
        }

        public void setSellCommission(double sellCommission) {
            this.sellCommission = sellCommission;
        }

        public double getTotalCommissions() {
            return totalCommissions;
        }

        public void setTotalCommissions(double totalCommissions) {
            this.totalCommissions = totalCommissions;
        }

        // ✅ НОВЫЙ GETTER/SETTER: Прибыль БЕЗ комиссий
        public double getProfitWithoutCommission() {
            return profitWithoutCommission;
        }

        public void setProfitWithoutCommission(double profitWithoutCommission) {
            this.profitWithoutCommission = profitWithoutCommission;
        }

        public double getNetProfit() {
            return netProfit;
        }

        public void setNetProfit(double netProfit) {
            this.netProfit = netProfit;
        }

        public double getAdaptiveMarginPercent() {
            return adaptiveMarginPercent;
        }

        public void setAdaptiveMarginPercent(double adaptiveMarginPercent) {
            this.adaptiveMarginPercent = adaptiveMarginPercent;
        }
    }
}