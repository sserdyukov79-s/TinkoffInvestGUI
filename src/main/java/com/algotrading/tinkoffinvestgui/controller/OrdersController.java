package com.algotrading.tinkoffinvestgui.controller;

import com.algotrading.tinkoffinvestgui.model.Order;
import com.algotrading.tinkoffinvestgui.model.OrderRequest;
import com.algotrading.tinkoffinvestgui.service.OrderService;
import com.algotrading.tinkoffinvestgui.util.SwingUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Контроллер для управления заявками
 */
public class OrdersController {
    
    private final OrderService orderService;
    private final List<Consumer<List<Order>>> observers = new ArrayList<>();
    private List<Order> currentOrders = new ArrayList<>();
    
    public OrdersController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    public void addObserver(Consumer<List<Order>> observer) {
        observers.add(observer);
    }
    
    public void refreshOrders() {
        SwingUtils.runInBackground(
            () -> orderService.fetchOrders(),
            orders -> {
                this.currentOrders = orders;
                notifyObservers(orders);
            },
            error -> SwingUtils.showError(null, "Ошибка загрузки заявок", error)
        );
    }
    
    public void placeOrder(OrderRequest request, 
                          Consumer<String> onSuccess,
                          Consumer<Exception> onError) {
        SwingUtils.runInBackground(
            () -> orderService.placeOrder(request),
            orderId -> {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    onSuccess.accept(orderId);
                    refreshOrders();
                });
            },
            error -> javax.swing.SwingUtilities.invokeLater(() -> onError.accept(error))
        );
    }
    
    public void cancelOrder(String orderId,
                           Runnable onSuccess,
                           Consumer<Exception> onError) {
        SwingUtils.runInBackground(
            () -> {
                orderService.cancelOrder(orderId);
                return null;
            },
            result -> {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    onSuccess.run();
                    refreshOrders();
                });
            },
            error -> javax.swing.SwingUtilities.invokeLater(() -> onError.accept(error))
        );
    }
    
    public List<Order> getCurrentOrders() {
        return currentOrders;
    }
    
    private void notifyObservers(List<Order> orders) {
        javax.swing.SwingUtilities.invokeLater(() -> 
            observers.forEach(observer -> observer.accept(orders))
        );
    }
}
