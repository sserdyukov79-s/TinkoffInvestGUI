package com.algotrading.tinkoffinvestgui.model;

import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.OrderType;
import java.math.BigDecimal;

/**
 * Модель запроса на создание заявки
 */
public class OrderRequest {
    private final String figi;
    private final long quantity;
    private final BigDecimal price;
    private final OrderDirection direction;
    private final String accountId;
    private final OrderType orderType;
    
    private OrderRequest(Builder builder) {
        this.figi = builder.figi;
        this.quantity = builder.quantity;
        this.price = builder.price;
        this.direction = builder.direction;
        this.accountId = builder.accountId;
        this.orderType = builder.orderType;
    }
    
    public String getFigi() { return figi; }
    public long getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public OrderDirection getDirection() { return direction; }
    public String getAccountId() { return accountId; }
    public OrderType getOrderType() { return orderType; }
    
    public static class Builder {
        private String figi;
        private long quantity = 1;
        private BigDecimal price = BigDecimal.ZERO;
        private OrderDirection direction = OrderDirection.ORDER_DIRECTION_BUY;
        private String accountId;
        private OrderType orderType = OrderType.ORDER_TYPE_LIMIT;
        
        public Builder figi(String figi) { this.figi = figi; return this; }
        public Builder quantity(long quantity) { this.quantity = quantity; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder direction(OrderDirection direction) { this.direction = direction; return this; }
        public Builder accountId(String accountId) { this.accountId = accountId; return this; }
        public Builder orderType(OrderType orderType) { this.orderType = orderType; return this; }
        
        public OrderRequest build() {
            if (figi == null || figi.isEmpty()) {
                throw new IllegalStateException("FIGI обязателен");
            }
            if (accountId == null || accountId.isEmpty()) {
                throw new IllegalStateException("Account ID обязателен");
            }
            if (quantity <= 0) {
                throw new IllegalStateException("Количество должно быть больше 0");
            }
            return new OrderRequest(this);
        }
    }
}
