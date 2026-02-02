package com.algotrading.tinkoffinvestgui.util;

import ru.tinkoff.piapi.contract.v1.MoneyValue;
import ru.tinkoff.piapi.contract.v1.Quotation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Утилита для конвертации денежных значений из Tinkoff API
 * 
 * Используйте этот класс для преобразования MoneyValue и Quotation в BigDecimal
 */
public class MoneyConverter {
    
    /**
     * Конвертирует MoneyValue в BigDecimal
     * 
     * @param moneyValue значение из API
     * @return BigDecimal представление суммы
     */
    public static BigDecimal toBigDecimal(MoneyValue moneyValue) {
        if (moneyValue == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal units = BigDecimal.valueOf(moneyValue.getUnits());
        BigDecimal nano = BigDecimal.valueOf(moneyValue.getNano())
                .divide(BigDecimal.valueOf(1_000_000_000), 9, RoundingMode.HALF_UP);
        
        return units.add(nano);
    }
    
    /**
     * Конвертирует Quotation в BigDecimal
     * 
     * @param quotation котировка из API
     * @return BigDecimal представление цены
     */
    public static BigDecimal toBigDecimal(Quotation quotation) {
        if (quotation == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal units = BigDecimal.valueOf(quotation.getUnits());
        BigDecimal nano = BigDecimal.valueOf(quotation.getNano())
                .divide(BigDecimal.valueOf(1_000_000_000), 9, RoundingMode.HALF_UP);
        
        return units.add(nano);
    }
    
    /**
     * Конвертирует BigDecimal обратно в MoneyValue
     * 
     * @param value сумма
     * @param currency валюта (например, "RUB", "USD")
     * @return MoneyValue для использования в API
     */
    public static MoneyValue toMoneyValue(BigDecimal value, String currency) {
        if (value == null) {
            value = BigDecimal.ZERO;
        }
        
        long units = value.longValue();
        int nano = value.remainder(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(1_000_000_000))
                .intValue();
        
        return MoneyValue.newBuilder()
                .setUnits(units)
                .setNano(nano)
                .setCurrency(currency)
                .build();
    }
    
    /**
     * Конвертирует BigDecimal обратно в Quotation
     * 
     * @param value цена
     * @return Quotation для использования в API
     */
    public static Quotation toQuotation(BigDecimal value) {
        if (value == null) {
            value = BigDecimal.ZERO;
        }
        
        long units = value.longValue();
        int nano = value.remainder(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(1_000_000_000))
                .intValue();
        
        return Quotation.newBuilder()
                .setUnits(units)
                .setNano(nano)
                .build();
    }
}
