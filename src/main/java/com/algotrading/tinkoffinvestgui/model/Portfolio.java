package com.algotrading.tinkoffinvestgui.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Модель портфеля
 */
public class Portfolio {
    private final List<Position> positions;
    private final BigDecimal totalValue;
    private final BigDecimal totalProfitLoss;
    private final String currency;
    
    private Portfolio(Builder builder) {
        this.positions = new ArrayList<>(builder.positions);
        this.totalValue = builder.totalValue;
        this.totalProfitLoss = builder.totalProfitLoss;
        this.currency = builder.currency;
    }
    
    public List<Position> getPositions() {
        return Collections.unmodifiableList(positions);
    }
    
    public BigDecimal getTotalValue() {
        return totalValue;
    }
    
    public BigDecimal getTotalProfitLoss() {
        return totalProfitLoss;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public int getPositionCount() {
        return positions.size();
    }
    
    public static class Builder {
        private final List<Position> positions = new ArrayList<>();
        private BigDecimal totalValue = BigDecimal.ZERO;
        private BigDecimal totalProfitLoss = BigDecimal.ZERO;
        private String currency = "RUB";
        
        public Builder addPosition(Position position) {
            this.positions.add(position);
            return this;
        }
        
        public Builder positions(List<Position> positions) {
            this.positions.clear();
            this.positions.addAll(positions);
            return this;
        }
        
        public Builder totalValue(BigDecimal totalValue) {
            this.totalValue = totalValue;
            return this;
        }
        
        public Builder totalProfitLoss(BigDecimal totalProfitLoss) {
            this.totalProfitLoss = totalProfitLoss;
            return this;
        }
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public Portfolio build() {
            return new Portfolio(this);
        }
    }
}
