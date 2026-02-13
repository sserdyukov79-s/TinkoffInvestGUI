package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.model.Order;
import com.algotrading.tinkoffinvestgui.model.OrderRequest;
import com.algotrading.tinkoffinvestgui.util.MoneyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.OrderState;

import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для работы с заявками (слой для GUI поверх TinkoffApiService)
 */
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final TinkoffApiService apiService;

    public OrderService(TinkoffApiService apiService) {
        this.apiService = apiService;
    }

    /**
     * Получить список всех заявок из API
     */
    public List<Order> fetchOrders() {
        log.info("📥 Получение списка заявок из API");

        List<OrderState> apiOrders = apiService.getOrders();
        List<Order> orders = new ArrayList<>();

        for (OrderState apiOrder : apiOrders) {
            Order order = new Order.Builder()
                    .orderId(apiOrder.getOrderId())
                    .figi(apiOrder.getFigi())
                    .direction(apiOrder.getDirection())
                    .initialOrderPrice(MoneyConverter.toBigDecimal(apiOrder.getInitialOrderPrice()))
                    .lotsRequested(apiOrder.getLotsRequested())
                    .lotsExecuted(apiOrder.getLotsExecuted())
                    .executionReportStatus(apiOrder.getExecutionReportStatus())
                    .currency(apiOrder.getCurrency())
                    .build();
            orders.add(order);
        }

        log.info("✅ Получено {} заявок", orders.size());
        return orders;
    }

    /**
     * Разместить новую заявку
     */
    public String placeOrder(OrderRequest request) {
        log.info("📤 Размещение заявки: FIGI={}, цена={}, количество={}",
                request.getFigi(), request.getPrice(), request.getQuantity());

        String orderId = apiService.postOrder(
                request.getFigi(),
                request.getQuantity(),
                MoneyConverter.toQuotation(request.getPrice()),
                request.getDirection(),
                request.getOrderType()
        );

        log.info("✅ Заявка размещена: ID={}", orderId);
        return orderId;
    }

    /**
     * Отменить заявку по биржевому ID (вызывается из GUI-контроллера)
     */
    public void cancelOrder(String orderId) {
        apiService.cancelOrder(orderId);
    }

    /**
     * Получить активные заявки (NEW / PARTIALLYFILL и т.п.)
     */
    public List<Order> fetchActiveOrders() {
        List<Order> allOrders = fetchOrders();
        List<Order> activeOrders = new ArrayList<>();

        for (Order order : allOrders) {
            if (order.isActive()) {
                activeOrders.add(order);
            }
        }

        log.info("📋 Активных заявок: {}", activeOrders.size());
        return activeOrders;
    }

    /**
     * Формирование JSON предпросмотра заявок для UI по списку инструментов
     */
    public String createOrdersJson(List<Instrument> instruments, String accountId) {
        return com.algotrading.tinkoffinvestgui.api.OrdersService.createOrdersJson(instruments, accountId);
    }
}