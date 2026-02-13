package com.algotrading.tinkoffinvestgui.util;

import ru.tinkoff.piapi.contract.v1.MoneyValue;
import ru.tinkoff.piapi.contract.v1.Quotation;

import java.math.BigDecimal;

/**
 * Утилита для конвертации Money/Quotation типов из API в BigDecimal
 */
public class MoneyConverter {

    /**
     * Конвертация MoneyValue в BigDecimal
     */
    public static BigDecimal toBigDecimal(MoneyValue money) {
        if (money == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal units = BigDecimal.valueOf(money.getUnits());
        BigDecimal nanos = BigDecimal.valueOf(money.getNano())
                .divide(BigDecimal.valueOf(1_000_000_000), 9, BigDecimal.ROUND_HALF_UP);

        return units.add(nanos);
    }

    /**
     * Конвертация Quotation в BigDecimal
     */
    public static BigDecimal toBigDecimal(Quotation quotation) {
        if (quotation == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal units = BigDecimal.valueOf(quotation.getUnits());
        BigDecimal nanos = BigDecimal.valueOf(quotation.getNano())
                .divide(BigDecimal.valueOf(1_000_000_000), 9, BigDecimal.ROUND_HALF_UP);

        return units.add(nanos);
    }

    /**
     * Конвертация BigDecimal в Quotation
     */
    public static Quotation toQuotation(BigDecimal value) {
        if (value == null) {
            value = BigDecimal.ZERO;
        }

        long units = value.longValue();
        int nano = value.subtract(BigDecimal.valueOf(units))
                .multiply(BigDecimal.valueOf(1_000_000_000))
                .intValue();

        return Quotation.newBuilder()
                .setUnits(units)
                .setNano(nano)
                .build();
    }

    /**
     * Конвертация BigDecimal в MoneyValue
     */
    public static MoneyValue toMoneyValue(BigDecimal value, String currency) {
        if (value == null) {
            value = BigDecimal.ZERO;
        }

        long units = value.longValue();
        int nano = value.subtract(BigDecimal.valueOf(units))
                .multiply(BigDecimal.valueOf(1_000_000_000))
                .intValue();

        return MoneyValue.newBuilder()
                .setUnits(units)
                .setNano(nano)
                .setCurrency(currency)
                .build();
    }
}
