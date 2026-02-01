package com.algotrading.tinkoffinvestgui.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Расчётчик рекомендованных цен покупки/продажи для стратегии "ловец дна"
 * На основе волатильности последних 4 месяцев
 * С учётом комиссии брокера 0.04% на каждую операцию
 */
public class BondStrategyCalculator {
    private static final Logger log = LoggerFactory.getLogger(BondStrategyCalculator.class);

    // Параметры стратегии
    private static final double VOLATILITY_MULTIPLIER = 1.2;  // k для расчёта цены покупки
    private static final double BROKER_COMMISSION = 0.0004;   // 0.04% комиссия брокера
    private static final double PROFIT_MARGIN = 0.008;        // 0.8% прибыли при продаже

    /**
     * Рассчитывает рекомендованные цены на основе волатильности
     * С учётом комиссии брокера 0.04% на покупку и продажу
     */
    public static StrategyRecommendation calculatePrices(
            BondsAnalysisService.BondAnalysisResult analysis) {

        double lastPrice = analysis.getCurrentPrice();
        double volatility = analysis.getVolatility();
        double avgPrice = analysis.getAvgPrice();
        double volatilityPercent = (volatility / avgPrice) * 100;

        // ЦЕНА ПОКУПКИ: вчерашняя цена минус 1.2 × волатильность
        // Цель: поймать случайный сброс ниже текущей цены
        double buyPriceDiscount = VOLATILITY_MULTIPLIER * volatility;
        double buyPrice = lastPrice - buyPriceDiscount;

        // КОМИССИЯ НА ПОКУПКУ: 0.04% от цены покупки
        double buyCommission = buyPrice * BROKER_COMMISSION;

        // ЦЕНА ПРОДАЖИ: цена покупки + маржа (0.8%) + компенсация комиссий
        // Нужно покрыть: комиссию покупки + комиссию продажи + получить прибыль
        double targetProfit = buyPrice * PROFIT_MARGIN;
        double sellCommission = (buyPrice + targetProfit) * BROKER_COMMISSION;
        double sellPrice = buyPrice + buyCommission + targetProfit + sellCommission;

        // Скидка от текущей цены (%)
        double discountPercent = ((lastPrice - buyPrice) / lastPrice) * 100;

        // ЧИСТЫЙ профит после всех комиссий (%)
        double totalCommissions = buyCommission + sellCommission;
        double netProfit = (sellPrice - buyPrice) - totalCommissions;
        double profitPercent = (netProfit / buyPrice) * 100;

        StrategyRecommendation rec = new StrategyRecommendation();
        rec.setCurrentPrice(BigDecimal.valueOf(lastPrice).setScale(2, RoundingMode.HALF_UP));
        rec.setVolatilityPercent(volatilityPercent);
        rec.setBuyPrice(BigDecimal.valueOf(buyPrice).setScale(2, RoundingMode.HALF_UP));
        rec.setSellPrice(BigDecimal.valueOf(sellPrice).setScale(2, RoundingMode.HALF_UP));
        rec.setDiscountPercent(discountPercent);
        rec.setProfitPercent(profitPercent);
        rec.setBuyCommission(buyCommission);
        rec.setSellCommission(sellCommission);
        rec.setTotalCommissions(totalCommissions);
        rec.setNetProfit(netProfit);

        // Рекомендация на основе волатильности
        String recommendation = buildRecommendation(volatilityPercent, analysis, rec);
        rec.setRecommendation(recommendation);

        log.debug("💡 Рекомендация для {}: купить по {}₽ (скидка {:.2f}%), " +
                        "продать по {}₽ (чистый профит {:.2f}% после комиссий {:.2f}₽)",
                analysis.getTicker(),
                buyPrice, discountPercent,
                sellPrice, profitPercent, totalCommissions);

        return rec;
    }

    /**
     * Формирует текстовую рекомендацию
     */
    private static String buildRecommendation(double volatilityPercent,
                                              BondsAnalysisService.BondAnalysisResult analysis,
                                              StrategyRecommendation rec) {
        StringBuilder sb = new StringBuilder();

        // Оценка волатильности
        if (volatilityPercent > 2.0) {
            sb.append("🔴 Высокий разброс цен (>2%) - отличные шансы на сброс\n");
        } else if (volatilityPercent > 1.0) {
            sb.append("🟡 Средний разброс (1-2%) - хорошие шансы\n");
        } else {
            sb.append("🟢 Низкий разброс (<1%) - консервативная заявка\n");
        }

        // Тренд
        if (analysis.getTrend() > 0) {
            sb.append("📈 Восходящий тренд - на рост\n");
        } else if (analysis.getTrend() < -0.1) {
            sb.append("📉 Нисходящий тренд - есть риск падения ниже\n");
        } else {
            sb.append("➡️ Боковой тренд - неопределённость\n");
        }

        // Dlong (берётся в обеспечение)
        if (analysis.getDlong() > 0) {
            sb.append(String.format("✅ Dlong = %.2f - берётся в обеспечение\n", analysis.getDlong()));
        } else {
            sb.append("❌ Нет Dlong - риск более высокий\n");
        }

        // Информация о комиссиях
        sb.append(String.format("\n💰 Комиссии брокера (0.04%%):\n"));
        sb.append(String.format("   • При покупке: %.2f₽\n", rec.getBuyCommission()));
        sb.append(String.format("   • При продаже: %.2f₽\n", rec.getSellCommission()));
        sb.append(String.format("   • Всего комиссий: %.2f₽\n", rec.getTotalCommissions()));
        sb.append(String.format("   • Чистая прибыль: %.2f₽ (%.2f%%)\n",
                rec.getNetProfit(), rec.getProfitPercent()));

        // Общая оценка
        double score = analysis.getScore();
        sb.append("\n");
        if (score > 80) {
            sb.append(String.format("⭐⭐⭐⭐⭐ Отличный кандидат (оценка %.0f)", score));
        } else if (score > 50) {
            sb.append(String.format("⭐⭐⭐⭐ Хороший кандидат (оценка %.0f)", score));
        } else if (score > 30) {
            sb.append(String.format("⭐⭐⭐ Средний кандидат (оценка %.0f)", score));
        } else {
            sb.append(String.format("⭐⭐ Низкая оценка (%.0f) - рискованно", score));
        }

        return sb.toString();
    }

    /**
     * DTO для рекомендации цен
     */
    public static class StrategyRecommendation {
        private BigDecimal currentPrice;
        private double volatilityPercent;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private double discountPercent;
        private double profitPercent;
        private String recommendation;

        // Новые поля для комиссий
        private double buyCommission;
        private double sellCommission;
        private double totalCommissions;
        private double netProfit;

        // Getters and Setters
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

        public double getVolatilityPercent() { return volatilityPercent; }
        public void setVolatilityPercent(double volatilityPercent) { this.volatilityPercent = volatilityPercent; }

        public BigDecimal getBuyPrice() { return buyPrice; }
        public void setBuyPrice(BigDecimal buyPrice) { this.buyPrice = buyPrice; }

        public BigDecimal getSellPrice() { return sellPrice; }
        public void setSellPrice(BigDecimal sellPrice) { this.sellPrice = sellPrice; }

        public double getDiscountPercent() { return discountPercent; }
        public void setDiscountPercent(double discountPercent) { this.discountPercent = discountPercent; }

        public double getProfitPercent() { return profitPercent; }
        public void setProfitPercent(double profitPercent) { this.profitPercent = profitPercent; }

        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

        // Новые геттеры/сеттеры для комиссий
        public double getBuyCommission() { return buyCommission; }
        public void setBuyCommission(double buyCommission) { this.buyCommission = buyCommission; }

        public double getSellCommission() { return sellCommission; }
        public void setSellCommission(double sellCommission) { this.sellCommission = sellCommission; }

        public double getTotalCommissions() { return totalCommissions; }
        public void setTotalCommissions(double totalCommissions) { this.totalCommissions = totalCommissions; }

        public double getNetProfit() { return netProfit; }
        public void setNetProfit(double netProfit) { this.netProfit = netProfit; }
    }
}