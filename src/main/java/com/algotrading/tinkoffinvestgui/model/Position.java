package com.algotrading.tinkoffinvestgui.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Модель позиции в портфеле
 */
public class Position {
    private final String figi;
    private final String ticker;
    private final String name;
    private final String instrumentType;
    private final BigDecimal quantity;
    private final BigDecimal averagePrice;
    private final BigDecimal currentPrice;
    private final String currency;
    
    private Position(Builder builder) {
        this.figi = builder.figi;
        this.ticker = builder.ticker;
        this.name = builder.name;
        this.instrumentType = builder.instrumentType;
        this.quantity = builder.quantity;
        this.averagePrice = builder.averagePrice;
        this.currentPrice = builder.currentPrice;
        this.currency = builder.currency;
    }
    
    public String getFigi() { return figi; }
    public String getTicker() { return ticker; }
    public String getName() { return name; }
    public String getInstrumentType() { return instrumentType; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAveragePrice() { return averagePrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public String getCurrency() { return currency; }
    
    public BigDecimal getTotalValue() {
        return currentPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }
    
    public BigDecimal getProfitLoss() {
        BigDecimal totalCost = averagePrice.multiply(quantity);
        BigDecimal totalValue = currentPrice.multiply(quantity);
        return totalValue.subtract(totalCost).setScale(2, RoundingMode.HALF_UP);
    }
    
    public BigDecimal getProfitLossPercent() {
        if (averagePrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal diff = currentPrice.subtract(averagePrice);
        return diff.divide(averagePrice, 4, RoundingMode.HALF_UP);
    }
    
    public static class Builder {
        private String figi;
        private String ticker;
        private String name;
        private String instrumentType;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal averagePrice = BigDecimal.ZERO;
        private BigDecimal currentPrice = BigDecimal.ZERO;
        private String currency = "RUB";
        
        public Builder figi(String figi) { this.figi = figi; return this; }
        public Builder ticker(String ticker) { this.ticker = ticker; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder instrumentType(String instrumentType) { this.instrumentType = instrumentType; return this; }
        public Builder quantity(BigDecimal quantity) { this.quantity = quantity; return this; }
        public Builder averagePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; return this; }
        public Builder currentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Position build() { return new Position(this); }
    }
}
