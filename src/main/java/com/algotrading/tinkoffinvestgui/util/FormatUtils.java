package com.algotrading.tinkoffinvestgui.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Утилита для форматирования чисел и денежных значений
 */
public class FormatUtils {
    
    private static final DecimalFormat MONEY_FORMAT;
    private static final DecimalFormat QUANTITY_FORMAT;
    private static final DecimalFormat PERCENT_FORMAT;
    
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator('.');
        
        MONEY_FORMAT = new DecimalFormat("#,##0.00", symbols);
        QUANTITY_FORMAT = new DecimalFormat("#,##0.##", symbols);
        PERCENT_FORMAT = new DecimalFormat("0.00", symbols);
    }
    
    /**
     * Форматирует денежную сумму
     * 
     * @param amount сумма
     * @param currency валюта
     * @return отформатированная строка (например, "1 234.56 RUB")
     */
    public static String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) {
            return "0.00 " + (currency != null ? currency : "");
        }
        
        String formatted = MONEY_FORMAT.format(amount);
        return formatted + " " + (currency != null ? currency : "");
    }
    
    /**
     * Форматирует количество (например, акций)
     * 
     * @param quantity количество
     * @return отформатированная строка (например, "10" или "5.5")
     */
    public static String formatQuantity(BigDecimal quantity) {
        if (quantity == null) {
            return "0";
        }
        
        return QUANTITY_FORMAT.format(quantity);
    }
    
    /**
     * Форматирует процент
     * 
     * @param percent процент (например, 0.15 для 15%)
     * @return отформатированная строка (например, "+15.00%" или "-5.50%")
     */
    public static String formatPercent(BigDecimal percent) {
        if (percent == null) {
            return "0.00%";
        }
        
        // Умножаем на 100 для отображения процентов
        BigDecimal percentValue = percent.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        
        String sign = percentValue.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
        return sign + PERCENT_FORMAT.format(percentValue) + "%";
    }
    
    /**
     * Форматирует прибыль/убыток с цветовым индикатором
     * 
     * @param profitLoss значение прибыли/убытка
     * @param currency валюта
     * @return отформатированная строка с знаком
     */
    public static String formatProfitLoss(BigDecimal profitLoss, String currency) {
        if (profitLoss == null) {
            return "0.00 " + (currency != null ? currency : "");
        }
        
        String sign = profitLoss.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
        String formatted = MONEY_FORMAT.format(profitLoss);
        return sign + formatted + " " + (currency != null ? currency : "");
    }
}
