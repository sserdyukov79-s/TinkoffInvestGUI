package com.algotrading.tinkoffinvestgui.model;

import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus;
import java.math.BigDecimal;

/**
 * Модель заявки
 */
public class Order {
    private final String orderId;
    private final String figi;
    private final OrderDirection direction;
    private final BigDecimal initialOrderPrice;
    private final long lotsRequested;
    private final long lotsExecuted;
    private final OrderExecutionReportStatus executionReportStatus;
    private final String currency;
    
    private Order(Builder builder) {
        this.orderId = builder.orderId;
        this.figi = builder.figi;
        this.direction = builder.direction;
        this.initialOrderPrice = builder.initialOrderPrice;
        this.lotsRequested = builder.lotsRequested;
        this.lotsExecuted = builder.lotsExecuted;
        this.executionReportStatus = builder.executionReportStatus;
        this.currency = builder.currency;
    }
    
    public String getOrderId() { return orderId; }
    public String getFigi() { return figi; }
    public OrderDirection getDirection() { return direction; }
    public BigDecimal getInitialOrderPrice() { return initialOrderPrice; }
    public long getLotsRequested() { return lotsRequested; }
    public long getLotsExecuted() { return lotsExecuted; }
    public OrderExecutionReportStatus getExecutionReportStatus() { return executionReportStatus; }
    public String getCurrency() { return currency; }
    
    public boolean isFullyExecuted() {
        return lotsExecuted == lotsRequested;
    }
    
    public boolean isActive() {
        return executionReportStatus == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_NEW ||
               executionReportStatus == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_PARTIALLYFILL;
    }
    
    public static class Builder {
        private String orderId;
        private String figi;
        private OrderDirection direction = OrderDirection.ORDER_DIRECTION_BUY;
        private BigDecimal initialOrderPrice = BigDecimal.ZERO;
        private long lotsRequested = 0;
        private long lotsExecuted = 0;
        private OrderExecutionReportStatus executionReportStatus = OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_UNSPECIFIED;
        private String currency = "RUB";
        
        public Builder orderId(String orderId) { this.orderId = orderId; return this; }
        public Builder figi(String figi) { this.figi = figi; return this; }
        public Builder direction(OrderDirection direction) { this.direction = direction; return this; }
        public Builder initialOrderPrice(BigDecimal initialOrderPrice) { this.initialOrderPrice = initialOrderPrice; return this; }
        public Builder lotsRequested(long lotsRequested) { this.lotsRequested = lotsRequested; return this; }
        public Builder lotsExecuted(long lotsExecuted) { this.lotsExecuted = lotsExecuted; return this; }
        public Builder executionReportStatus(OrderExecutionReportStatus executionReportStatus) { this.executionReportStatus = executionReportStatus; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Order build() { return new Order(this); }
    }
}
