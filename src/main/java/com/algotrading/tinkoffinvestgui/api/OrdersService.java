package com.algotrading.tinkoffinvestgui.api;

import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис для работы с заявками (Orders) через Tinkoff Invest API
 */
public class OrdersService {

    private static final Logger log = LoggerFactory.getLogger(OrdersService.class);

    private final String token;
    private final String apiUrl;
    private final int apiPort;
    private OrdersServiceGrpc.OrdersServiceBlockingStub ordersStub;
    private ManagedChannel channel;

    public OrdersService(String token, String apiUrl, int apiPort) {
        this.token = token;
        this.apiUrl = apiUrl;
        this.apiPort = apiPort;
        initializeChannel();
    }

    private void initializeChannel() {
        try {
            log.debug("Инициализация gRPC канала для Orders API: {}:{}", apiUrl, apiPort);

            channel = ManagedChannelBuilder
                    .forAddress(apiUrl, apiPort)
                    .useTransportSecurity()
                    .build();

            ordersStub = OrdersServiceGrpc.newBlockingStub(channel)
                    .withCallCredentials(new BearerTokenCallCredentials(token));

            log.info("✅ gRPC канал для Orders API успешно инициализирован");

        } catch (Exception e) {
            log.error("❌ Ошибка инициализации gRPC канала для Orders API", e);
            throw new RuntimeException("Не удалось подключиться к Orders API", e);
        }
    }

    /**
     * Отправляет заявку на ПОКУПКУ
     */
    public PostOrderResponse postBuyOrder(String accountId, String figi, int quantity, BigDecimal price) {
        log.info("\n════════════════════════════════════════════════════════════");
        log.info("=== ОТПРАВКА ЗАЯВКИ НА ПОКУПКУ ===");
        log.info("════════════════════════════════════════════════════════════");
        log.info("Account ID: {}", accountId);
        log.info("FIGI: {}", figi);
        log.info("Количество: {}", quantity);
        log.info("Цена: {}", price);

        try {
            PostOrderRequest request = buildOrderRequest(
                    accountId, figi, quantity, price, OrderDirection.ORDER_DIRECTION_BUY
            );

            // ✅ Логирование полного JSON запроса
            logPostOrderRequestJson(request, "BUY");

            PostOrderResponse response = ordersStub.postOrder(request);

            log.info("\n✅ ЗАЯВКА НА ПОКУПКУ ОТПРАВЛЕНА УСПЕШНО");
            log.info("Order ID: {}", response.getOrderId());
            log.info("Execution Report Status: {}", response.getExecutionReportStatus());
            log.info("Lots Requested: {}", response.getLotsRequested());
            log.info("Lots Executed: {}", response.getLotsExecuted());
            log.info("Initial Order Price: {} {}",
                    formatMoneyValue(response.getInitialOrderPrice()),
                    response.getInitialOrderPrice().getCurrency());
            log.info("Executed Order Price: {} {}",
                    formatMoneyValue(response.getExecutedOrderPrice()),
                    response.getExecutedOrderPrice().getCurrency());
            log.info("Total Order Amount: {} {}",
                    formatMoneyValue(response.getTotalOrderAmount()),
                    response.getTotalOrderAmount().getCurrency());
            log.info("════════════════════════════════════════════════════════════\n");

            return response;

        } catch (Exception e) {
            log.error("❌ Ошибка отправки заявки на ПОКУПКУ", e);
            throw new RuntimeException("Не удалось отправить заявку на покупку: " + e.getMessage(), e);
        }
    }

    /**
     * Отправляет заявку на ПРОДАЖУ
     */
    public PostOrderResponse postSellOrder(String accountId, String figi, int quantity, BigDecimal price) {
        log.info("\n════════════════════════════════════════════════════════════");
        log.info("=== ОТПРАВКА ЗАЯВКИ НА ПРОДАЖУ ===");
        log.info("════════════════════════════════════════════════════════════");
        log.info("Account ID: {}", accountId);
        log.info("FIGI: {}", figi);
        log.info("Количество: {}", quantity);
        log.info("Цена: {}", price);

        try {
            PostOrderRequest request = buildOrderRequest(
                    accountId, figi, quantity, price, OrderDirection.ORDER_DIRECTION_SELL
            );

            // ✅ Логирование полного JSON запроса
            logPostOrderRequestJson(request, "SELL");

            PostOrderResponse response = ordersStub.postOrder(request);

            log.info("\n✅ ЗАЯВКА НА ПРОДАЖУ ОТПРАВЛЕНА УСПЕШНО");
            log.info("Order ID: {}", response.getOrderId());
            log.info("Execution Report Status: {}", response.getExecutionReportStatus());
            log.info("Lots Requested: {}", response.getLotsRequested());
            log.info("Lots Executed: {}", response.getLotsExecuted());
            log.info("Initial Order Price: {} {}",
                    formatMoneyValue(response.getInitialOrderPrice()),
                    response.getInitialOrderPrice().getCurrency());
            log.info("Executed Order Price: {} {}",
                    formatMoneyValue(response.getExecutedOrderPrice()),
                    response.getExecutedOrderPrice().getCurrency());
            log.info("Total Order Amount: {} {}",
                    formatMoneyValue(response.getTotalOrderAmount()),
                    response.getTotalOrderAmount().getCurrency());
            log.info("════════════════════════════════════════════════════════════\n");

            return response;

        } catch (Exception e) {
            log.error("❌ Ошибка отправки заявки на ПРОДАЖУ", e);
            throw new RuntimeException("Не удалось отправить заявку на продажу: " + e.getMessage(), e);
        }
    }

    /**
     * Строит PostOrderRequest для отправки заявки
     */
    private PostOrderRequest buildOrderRequest(String accountId, String figi, int quantity,
                                               BigDecimal price, OrderDirection direction) {

        String orderId = generateOrderId();

        Quotation priceQuotation = buildQuotation(price);

        return PostOrderRequest.newBuilder()
                .setAccountId(accountId)
                .setFigi(figi)
                .setQuantity(quantity)
                .setPrice(priceQuotation)
                .setDirection(direction)
                .setOrderType(OrderType.ORDER_TYPE_LIMIT)
                .setOrderId(orderId)
                .build();
    }

    /**
     * Конвертирует BigDecimal в Quotation (units + nano)
     */
    private Quotation buildQuotation(BigDecimal value) {
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
     * Генерирует уникальный Order ID
     */
    private String generateOrderId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Форматирует MoneyValue для вывода
     */
    private String formatMoneyValue(MoneyValue money) {
        double value = money.getUnits() + money.getNano() / 1_000_000_000.0;
        return String.format("%.2f", value);
    }

    /**
     * ✅ ЛОГИРОВАНИЕ ПОЛНОГО JSON ЗАПРОСА PostOrderRequest
     * Компактный формат аналогичный CandlesApiService
     */
    private void logPostOrderRequestJson(PostOrderRequest request, String orderType) {
        try {
            // Конвертируем Protobuf в JSON с красивым форматированием
            String json = JsonFormat.printer()
                    .includingDefaultValueFields()
                    .print(request);

            // Компактный многострочный формат
            log.info("📤 Полный JSON запрос на {} заявку:\n{}", orderType, json);

        } catch (Exception e) {
            log.error("Ошибка форматирования JSON для PostOrderRequest", e);
        }
    }


    /**
     * ✅ Создаёт JSON представление заявок для предпросмотра
     */
    public static String createOrdersJson(List<Instrument> instruments, String accountId) {
        List<Map<String, Object>> orders = new ArrayList<>();

        for (Instrument instrument : instruments) {
            // Заявка на покупку
            if (instrument.getBuyPrice() != null &&
                    instrument.getBuyQuantity() != null &&
                    instrument.getBuyQuantity() > 0) {

                Map<String, Object> buyOrder = new HashMap<>();
                buyOrder.put("accountId", accountId);
                buyOrder.put("figi", instrument.getFigi());
                buyOrder.put("quantity", instrument.getBuyQuantity());
                buyOrder.put("price", buildPriceMap(instrument.getBuyPrice()));
                buyOrder.put("direction", "ORDER_DIRECTION_BUY");
                buyOrder.put("orderType", "ORDER_TYPE_LIMIT");
                buyOrder.put("orderId", "ORDER_" + System.currentTimeMillis() + "_BUY_" + instrument.getId());
                buyOrder.put("instrumentName", instrument.getName());

                orders.add(buyOrder);
            }

            // Заявка на продажу
            if (instrument.getSellPrice() != null &&
                    instrument.getSellQuantity() != null &&
                    instrument.getSellQuantity() > 0) {

                Map<String, Object> sellOrder = new HashMap<>();
                sellOrder.put("accountId", accountId);
                sellOrder.put("figi", instrument.getFigi());
                sellOrder.put("quantity", instrument.getSellQuantity());
                sellOrder.put("price", buildPriceMap(instrument.getSellPrice()));
                sellOrder.put("direction", "ORDER_DIRECTION_SELL");
                sellOrder.put("orderType", "ORDER_TYPE_LIMIT");
                sellOrder.put("orderId", "ORDER_" + System.currentTimeMillis() + "_SELL_" + instrument.getId());
                sellOrder.put("instrumentName", instrument.getName());

                orders.add(sellOrder);
            }
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(orders);
    }

    /**
     * Вспомогательный метод для создания структуры цены
     */
    private static Map<String, Object> buildPriceMap(BigDecimal price) {
        long units = price.longValue();
        int nano = price.subtract(BigDecimal.valueOf(units))
                .multiply(BigDecimal.valueOf(1_000_000_000))
                .intValue();

        Map<String, Object> priceMap = new HashMap<>();
        priceMap.put("units", units);
        priceMap.put("nano", nano);
        return priceMap;
    }

    /**
     * Закрытие gRPC канала
     */
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            log.info("Закрытие gRPC канала для Orders API");
            channel.shutdown();
        }
    }
}
