package com.algotrading.tinkoffinvestgui.model;

import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus;

import java.math.BigDecimal;
import java.time.Instant;

public class Order {

    private Integer id;
    private String orderId;

    private String myOrderId;
    private String exchangeOrderId;
    private String accountId;

    private String figi;
    private String ticker;
    private String instrumentName;

    private OrderDirection direction;
    private String orderType;

    private long lotsRequested;
    private long lotsExecuted;

    private BigDecimal price;
    private BigDecimal initialOrderPrice;
    private BigDecimal averageExecutionPrice;

    private OrderExecutionReportStatus executionReportStatus;
    private String status;

    private BigDecimal totalOrderAmount;
    private BigDecimal commission;
    private BigDecimal aci;

    private String parentOrderId;
    private Instant parentFillTime;

    private String errorMessage;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant executedAt;
    private Instant cancelledAt;
    private Instant submittedAt;  // <<<< НОВОЕ ПОЛЕ

    private String currency;

    public Order() {
        this.currency = "RUB";
        this.direction = OrderDirection.ORDER_DIRECTION_BUY;
        this.executionReportStatus = OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_UNSPECIFIED;
    }

    private Order(Builder builder) {
        this();
        this.orderId = builder.orderId;
        this.figi = builder.figi;
        this.direction = builder.direction;
        this.initialOrderPrice = builder.initialOrderPrice;
        this.lotsRequested = builder.lotsRequested;
        this.lotsExecuted = builder.lotsExecuted;
        this.executionReportStatus = builder.executionReportStatus;
        this.currency = builder.currency;
    }

    // === Getters ===

    public Integer getId() { return id; }
    public String getMyOrderId() { return myOrderId; }
    public String getExchangeOrderId() { return exchangeOrderId; }
    public String getAccountId() { return accountId; }

    public String getOrderId() { return orderId; }
    public String getFigi() { return figi; }
    public String getTicker() { return ticker; }
    public String getInstrumentName() { return instrumentName; }

    public OrderDirection getDirection() { return direction; }
    public String getOrderType() { return orderType; }

    public long getLotsRequested() { return lotsRequested; }
    public long getLotsExecuted() { return lotsExecuted; }

    public BigDecimal getPrice() { return price; }
    public BigDecimal getInitialOrderPrice() { return initialOrderPrice; }
    public BigDecimal getAverageExecutionPrice() { return averageExecutionPrice; }

    public OrderExecutionReportStatus getExecutionReportStatus() { return executionReportStatus; }
    public String getStatus() { return status; }

    public BigDecimal getTotalOrderAmount() { return totalOrderAmount; }
    public BigDecimal getCommission() { return commission; }
    public BigDecimal getAci() { return aci; }

    public String getParentOrderId() { return parentOrderId; }
    public Instant getParentFillTime() { return parentFillTime; }

    public String getErrorMessage() { return errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExecutedAt() { return executedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getSubmittedAt() { return submittedAt; }  // <<<< НОВЫЙ GETTER

    public String getCurrency() { return currency; }

    public boolean isFullyExecuted() {
        return lotsExecuted >= lotsRequested;
    }

    public boolean isActive() {
        return executionReportStatus == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_NEW
                || executionReportStatus == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_PARTIALLYFILL;
    }

    // === Setters ===

    public void setId(Integer id) { this.id = id; }
    public void setMyOrderId(String myOrderId) { this.myOrderId = myOrderId; }
    public void setExchangeOrderId(String exchangeOrderId) { this.exchangeOrderId = exchangeOrderId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public void setOrderId(String orderId) { this.orderId = orderId; }
    public void setFigi(String figi) { this.figi = figi; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public void setInstrumentName(String instrumentName) { this.instrumentName = instrumentName; }

    public void setDirection(OrderDirection direction) { this.direction = direction; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public void setLotsRequested(long lotsRequested) { this.lotsRequested = lotsRequested; }
    public void setLotsExecuted(long lotsExecuted) { this.lotsExecuted = lotsExecuted; }

    public void setPrice(BigDecimal price) { this.price = price; }
    public void setInitialOrderPrice(BigDecimal initialOrderPrice) { this.initialOrderPrice = initialOrderPrice; }
    public void setAverageExecutionPrice(BigDecimal averageExecutionPrice) { this.averageExecutionPrice = averageExecutionPrice; }

    public void setExecutionReportStatus(OrderExecutionReportStatus executionReportStatus) { this.executionReportStatus = executionReportStatus; }
    public void setStatus(String status) { this.status = status; }

    public void setTotalOrderAmount(BigDecimal totalOrderAmount) { this.totalOrderAmount = totalOrderAmount; }
    public void setCommission(BigDecimal commission) { this.commission = commission; }
    public void setAci(BigDecimal aci) { this.aci = aci; }

    public void setParentOrderId(String parentOrderId) { this.parentOrderId = parentOrderId; }
    public void setParentFillTime(Instant parentFillTime) { this.parentFillTime = parentFillTime; }

    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }  // <<<< НОВЫЙ SETTER

    public void setCurrency(String currency) { this.currency = currency; }

    // === Builder (для совместимости с существующим кодом OrderService) ===

    public static class Builder {
        private String orderId;
        private String figi;
        private OrderDirection direction = OrderDirection.ORDER_DIRECTION_BUY;
        private BigDecimal initialOrderPrice = BigDecimal.ZERO;
        private long lotsRequested = 0L;
        private long lotsExecuted = 0L;
        private OrderExecutionReportStatus executionReportStatus =
                OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_UNSPECIFIED;
        private String currency = "RUB";

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder figi(String figi) {
            this.figi = figi;
            return this;
        }

        public Builder direction(OrderDirection direction) {
            this.direction = direction;
            return this;
        }

        public Builder initialOrderPrice(BigDecimal initialOrderPrice) {
            this.initialOrderPrice = initialOrderPrice;
            return this;
        }

        public Builder lotsRequested(long lotsRequested) {
            this.lotsRequested = lotsRequested;
            return this;
        }

        public Builder lotsExecuted(long lotsExecuted) {
            this.lotsExecuted = lotsExecuted;
            return this;
        }

        public Builder executionReportStatus(OrderExecutionReportStatus executionReportStatus) {
            this.executionReportStatus = executionReportStatus;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Order build() {
            return new Order(this);
        }
    }
}
