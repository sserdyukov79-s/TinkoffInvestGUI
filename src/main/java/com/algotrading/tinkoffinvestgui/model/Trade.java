package com.algotrading.tinkoffinvestgui.model;

import java.math.BigDecimal;
import java.time.Instant;

public class Trade {

    private Integer id;
    private String tradeId;
    private String orderId;
    private String accountId;

    private String figi;
    private String ticker;
    private String instrumentName;
    private String instrumentType;

    private String direction;  // BUY/SELL
    private long quantity;
    private BigDecimal price;

    private BigDecimal tradeAmount;
    private BigDecimal commission;
    private BigDecimal aci;
    private BigDecimal yieldValue;

    private Instant tradeDate;
    private Instant createdAt;
    private Instant updatedAt;

    private String currency;
    private String exchange;

    // Конструктор
    public Trade() {
        this.currency = "RUB";
    }

    // Getters
    public Integer getId() { return id; }
    public String getTradeId() { return tradeId; }
    public String getOrderId() { return orderId; }
    public String getAccountId() { return accountId; }
    public String getFigi() { return figi; }
    public String getTicker() { return ticker; }
    public String getInstrumentName() { return instrumentName; }
    public String getInstrumentType() { return instrumentType; }
    public String getDirection() { return direction; }
    public long getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getTradeAmount() { return tradeAmount; }
    public BigDecimal getCommission() { return commission; }
    public BigDecimal getAci() { return aci; }
    public BigDecimal getYieldValue() { return yieldValue; }
    public Instant getTradeDate() { return tradeDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCurrency() { return currency; }
    public String getExchange() { return exchange; }

    // Setters
    public void setId(Integer id) { this.id = id; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public void setFigi(String figi) { this.figi = figi; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public void setInstrumentName(String instrumentName) { this.instrumentName = instrumentName; }
    public void setInstrumentType(String instrumentType) { this.instrumentType = instrumentType; }
    public void setDirection(String direction) { this.direction = direction; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setTradeAmount(BigDecimal tradeAmount) { this.tradeAmount = tradeAmount; }
    public void setCommission(BigDecimal commission) { this.commission = commission; }
    public void setAci(BigDecimal aci) { this.aci = aci; }
    public void setYieldValue(BigDecimal yieldValue) { this.yieldValue = yieldValue; }
    public void setTradeDate(Instant tradeDate) { this.tradeDate = tradeDate; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setExchange(String exchange) { this.exchange = exchange; }
}
