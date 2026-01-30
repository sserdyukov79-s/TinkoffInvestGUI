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
     * Формирует список заявок для инструмента (может быть 0, 1 или 2 заявки)
     */
    public static List<OrderRequest> createOrdersForInstrument(Instrument instrument, String accountId) {
        List<OrderRequest> orders = new ArrayList<>();

        // Проверяем заявку на ПОКУПКУ
        if (instrument.getBuyQuantity() != null && instrument.getBuyQuantity() > 0) {
            OrderRequest buyOrder = createOrder(
                    instrument,
                    accountId,
                    "ORDER_DIRECTION_BUY",
                    instrument.getBuyQuantity(),
                    instrument.getBuyPrice()
            );
            if (buyOrder != null) {
                orders.add(buyOrder);
            }
        }

        // Проверяем заявку на ПРОДАЖУ
        if (instrument.getSellQuantity() != null && instrument.getSellQuantity() > 0) {
            OrderRequest sellOrder = createOrder(
                    instrument,
                    accountId,
                    "ORDER_DIRECTION_SELL",
                    instrument.getSellQuantity(),
                    instrument.getSellPrice()
            );
            if (sellOrder != null) {
                orders.add(sellOrder);
            }
        }

        return orders;
    }

    /**
     * Создаёт одну заявку
     */
    private static OrderRequest createOrder(
            Instrument instrument,
            String accountId,
            String direction,
            Integer quantity,
            java.math.BigDecimal price) {

        if (quantity == null || quantity <= 0) {
            return null;
        }

        OrderRequest order = new OrderRequest();

        // instrumentId (figi)
        order.instrumentId = instrument.getFigi();

        // accountId
        order.accountId = accountId;

        // direction
        order.direction = direction;

        // quantity
        order.quantity = String.valueOf(quantity);

        // price
        if (price != null) {
            order.price = convertToQuotation(price);
        }

        // orderType
        order.orderType = "ORDER_TYPE_LIMIT";

        // orderId = дата+время+счетчик
        order.orderId = generateOrderId();

        // timeInForce
        order.timeInForce = "TIME_IN_FORCE_DAY";

        // priceType (по умолчанию - валюта)
        order.priceType = "PRICE_TYPE_CURRENCY";

        return order;
    }

    /**
     * Формирует JSON заявки для инструмента (УСТАРЕЛО - используйте createOrdersForInstrument)
     */
    @Deprecated
    public static String createOrderJson(Instrument instrument, String accountId) {
        List<OrderRequest> orders = createOrdersForInstrument(instrument, accountId);
        if (orders.isEmpty()) {
            return null;
        }
        return gson.toJson(orders.get(0)); // Возвращаем только первую для совместимости
    }

    /**
     * Формирует JSON для всех инструментов (ОБНОВЛЕНО)
     */
    public static String createOrdersJson(List<Instrument> instruments, String accountId) {
        List<OrderRequest> allOrders = new ArrayList<>();

        for (Instrument instrument : instruments) {
            List<OrderRequest> orders = createOrdersForInstrument(instrument, accountId);
            allOrders.addAll(orders);
        }

        return gson.toJson(allOrders);
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
