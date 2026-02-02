package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.model.Order;
import com.algotrading.tinkoffinvestgui.model.OrderRequest;
import com.algotrading.tinkoffinvestgui.util.MoneyConverter;
import ru.tinkoff.piapi.contract.v1.OrderState;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для работы с заявками
 */
public class OrderService {
    
    private final TinkoffApiService apiService;
    
    public OrderService(TinkoffApiService apiService) {
        this.apiService = apiService;
    }
    
    public List<Order> fetchOrders() {
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
        
        return orders;
    }
    
    public String placeOrder(OrderRequest request) {
        return apiService.postOrder(
                request.getFigi(),
                request.getQuantity(),
                MoneyConverter.toQuotation(request.getPrice()),
                request.getDirection(),
                request.getOrderType()
        );
    }
    
    public void cancelOrder(String orderId) {
        apiService.cancelOrder(orderId);
    }
    
    public List<Order> fetchActiveOrders() {
        List<Order> allOrders = fetchOrders();
        List<Order> activeOrders = new ArrayList<>();
        
        for (Order order : allOrders) {
            if (order.isActive()) {
                activeOrders.add(order);
            }
        }
        
        return activeOrders;
    }
}
