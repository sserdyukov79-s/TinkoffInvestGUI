package com.algotrading.tinkoffinvestgui.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Instrument {
    private Integer id;
    private Integer priority;
    private String figi;
    private String name;
    private String isin;
    private BigDecimal buyPrice;
    private Integer buyQuantity;
    private BigDecimal sellPrice;
    private Integer sellQuantity;
    private BigDecimal sellPriceFixed;
    private LocalDate sellPriceFixedDate;
    private BigDecimal manualBuyPrice;
    private BigDecimal manualSellPrice;


    // ✅ КОНСТРУКТОР ПО УМОЛЧАНИЮ
    public Instrument() {
        this.priority = 1;
    }

    // ✅ КОНСТРУКТОР С ПАРАМЕТРАМИ
    public Instrument(String figi, String name, String isin, Integer priority) {
        this.figi = figi;
        this.name = name;
        this.isin = isin;
        this.priority = priority != null ? priority : 1;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getFigi() {
        return figi;
    }

    public void setFigi(String figi) {
        this.figi = figi;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public BigDecimal getBuyPrice() {
        return buyPrice;
    }

    public void setBuyPrice(BigDecimal buyPrice) {
        this.buyPrice = buyPrice;
    }

    public Integer getBuyQuantity() {
        return buyQuantity;
    }

    public void setBuyQuantity(Integer buyQuantity) {
        this.buyQuantity = buyQuantity;
    }

    public BigDecimal getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(BigDecimal sellPrice) {
        this.sellPrice = sellPrice;
    }

    public Integer getSellQuantity() {
        return sellQuantity;
    }

    public void setSellQuantity(Integer sellQuantity) {
        this.sellQuantity = sellQuantity;
    }

    public BigDecimal getManualBuyPrice() {
        return manualBuyPrice;
    }

    public void setManualBuyPrice(BigDecimal manualBuyPrice) {
        this.manualBuyPrice = manualBuyPrice;
    }

    public BigDecimal getManualSellPrice() {
        return manualSellPrice;
    }

    public void setManualSellPrice(BigDecimal manualSellPrice) {
        this.manualSellPrice = manualSellPrice;
    }

    public BigDecimal getSellPriceFixed() {
        return sellPriceFixed;
    }

    public void setSellPriceFixed(BigDecimal sellPriceFixed) {
        this.sellPriceFixed = sellPriceFixed;
    }

    public LocalDate getSellPriceFixedDate() {
        return sellPriceFixedDate;
    }

    public void setSellPriceFixedDate(LocalDate sellPriceFixedDate) {
        this.sellPriceFixedDate = sellPriceFixedDate;
    }

    /**
     * Возвращает эффективную цену покупки: manual, если задана, иначе auto
     */
    public BigDecimal getEffectiveBuyPrice() {
        return manualBuyPrice != null ? manualBuyPrice : buyPrice;
    }

    /**
     * Возвращает эффективную цену продажи: manual, если задана, иначе auto
     */
    public BigDecimal getEffectiveSellPrice() {
        return manualSellPrice != null ? manualSellPrice : sellPrice;
    }

    @Override
    public String toString() {
        return "Instrument{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", isin='" + isin + '\'' +
                ", priority=" + priority +
                '}';
    }
}
