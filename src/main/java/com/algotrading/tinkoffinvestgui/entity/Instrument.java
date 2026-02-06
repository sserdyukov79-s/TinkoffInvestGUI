package com.algotrading.tinkoffinvestgui.entity;

import java.math.BigDecimal;
import java.time.LocalDate;




public class Instrument {

    private Integer id;
    private LocalDate bookdate;
    private String figi;
    private String name;
    private String isin;
    private Integer priority;
    private BigDecimal buyPrice;
    private Integer buyQuantity;
    private BigDecimal sellPrice;
    private Integer sellQuantity;

    // ✅ НОВЫЕ ПОЛЯ
    private BigDecimal manualBuyPrice;
    private BigDecimal manualSellPrice;


    public Instrument() {
        this.bookdate = LocalDate.now();
    }

    public Instrument(String figi, String name, String isin, Integer priority) {
        this();
        this.figi = figi;
        this.name = name;
        this.isin = isin;
        this.priority = priority;
    }

    // Getters & Setters (существующие)
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public LocalDate getBookdate() { return bookdate; }
    public void setBookdate(LocalDate bookdate) { this.bookdate = bookdate; }

    public String getFigi() { return figi; }
    public void setFigi(String figi) { this.figi = figi; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public BigDecimal getBuyPrice() { return buyPrice; }
    public void setBuyPrice(BigDecimal buyPrice) { this.buyPrice = buyPrice; }

    public Integer getBuyQuantity() { return buyQuantity; }
    public void setBuyQuantity(Integer buyQuantity) { this.buyQuantity = buyQuantity; }

    public BigDecimal getSellPrice() { return sellPrice; }
    public void setSellPrice(BigDecimal sellPrice) { this.sellPrice = sellPrice; }

    public Integer getSellQuantity() { return sellQuantity; }
    public void setSellQuantity(Integer sellQuantity) { this.sellQuantity = sellQuantity; }

    // ✅ НОВЫЕ ГЕТТЕРЫ/СЕТТЕРЫ
    public BigDecimal getManualBuyPrice() { return manualBuyPrice; }
    public void setManualBuyPrice(BigDecimal manualBuyPrice) { this.manualBuyPrice = manualBuyPrice; }

    public BigDecimal getManualSellPrice() { return manualSellPrice; }
    public void setManualSellPrice(BigDecimal manualSellPrice) { this.manualSellPrice = manualSellPrice; }

    // ✅ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ - ЭФФЕКТИВНЫЕ ЦЕНЫ
    /**
     * Возвращает эффективную цену покупки:
     * manual_buy_price если заполнено, иначе buy_price
     */
    public BigDecimal getEffectiveBuyPrice() {
        return manualBuyPrice != null ? manualBuyPrice : buyPrice;
    }

    /**
     * Возвращает эффективную цену продажи:
     * manual_sell_price если заполнено, иначе sell_price
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
                ", bookdate=" + bookdate +
                '}';
    }
}
