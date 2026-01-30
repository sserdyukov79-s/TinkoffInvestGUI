package com.algotrading.tinkoffinvestgui.api;

import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для формирования заявок T-Bank Invest API
 */
public class OrdersService {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static int orderCounter = 0;

    /**
     * Формирует JSON заявки для инструмента
     */
    public static String createOrderJson(Instrument instrument, String accountId) {
        OrderRequest order = new OrderRequest();

        // instrumentId (figi)
        order.instrumentId = instrument.getFigi();

        // accountId
        order.accountId = accountId;

        // Определяем направление и параметры на основе заполненных полей
        if (instrument.getBuyQuantity() != null && instrument.getBuyQuantity() > 0) {
            // Покупка
            order.direction = "ORDER_DIRECTION_BUY";
            order.quantity = String.valueOf(instrument.getBuyQuantity());

            if (instrument.getBuyPrice() != null) {
                order.price = convertToQuotation(instrument.getBuyPrice());
            }
        } else if (instrument.getSellQuantity() != null && instrument.getSellQuantity() > 0) {
            // Продажа
            order.direction = "ORDER_DIRECTION_SELL";
            order.quantity = String.valueOf(instrument.getSellQuantity());

            if (instrument.getSellPrice() != null) {
                order.price = convertToQuotation(instrument.getSellPrice());
            }
        } else {
            return null; // Нет данных для заявки
        }

        // orderType
        order.orderType = "ORDER_TYPE_LIMIT";

        // orderId = дата+время+счетчик
        order.orderId = generateOrderId();

        // timeInForce
        order.timeInForce = "TIME_IN_FORCE_DAY";

        // priceType (по умолчанию - валюта)
        order.priceType = "PRICE_TYPE_CURRENCY";

        return gson.toJson(order);
    }

    /**
     * Конвертирует BigDecimal в Quotation (units + nano) БЕЗ погрешности
     */
    private static Quotation convertToQuotation(java.math.BigDecimal price) {
        if (price == null) {
            return null;
        }

        Quotation quotation = new Quotation();

        // Целая часть
        quotation.units = String.valueOf(price.longValue());

        // Дробная часть (точное вычисление через BigDecimal)
        java.math.BigDecimal fractional = price.subtract(
                new java.math.BigDecimal(price.longValue())
        );

        // Умножаем на 1_000_000_000 и округляем
        java.math.BigDecimal nanoDecimal = fractional.multiply(
                new java.math.BigDecimal("1000000000")
        );

        quotation.nano = nanoDecimal.setScale(0, java.math.RoundingMode.HALF_UP).intValue();

        return quotation;
    }

    /**
     * Формирует JSON для всех инструментов
     */
    public static String createOrdersJson(List<Instrument> instruments, String accountId) {
        List<OrderRequest> orders = new ArrayList<>();

        for (Instrument instrument : instruments) {
            String json = createOrderJson(instrument, accountId);
            if (json != null) {
                OrderRequest order = gson.fromJson(json, OrderRequest.class);
                orders.add(order);
            }
        }

        return gson.toJson(orders);
    }

    /**
     * Генерирует уникальный orderId
     * Формат: YYYYMMDD_HHmmss_COUNTER
     */
    private static String generateOrderId() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        orderCounter++;
        return timestamp + "_" + String.format("%04d", orderCounter);
    }

    /**
     * Сбрасывает счётчик заявок
     */
    public static void resetCounter() {
        orderCounter = 0;
    }

    // ========== Inner Classes ==========

    /**
     * Класс запроса заявки
     */
    public static class OrderRequest {
        public String instrumentId;
        public String accountId;
        public String quantity;
        public Quotation price;
        public String direction;
        public String orderType;
        public String orderId;
        public String timeInForce;
        public String priceType;
    }

    /**
     * Класс котировки
     */
    public static class Quotation {
        public String units;  // Целая часть
        public int nano;      // Дробная часть (наноединицы)
    }
}
