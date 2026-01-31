package com.algotrading.tinkoffinvestgui.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO для создания/обновления инструмента
 */
public class InstrumentDTO {

    private Integer id;  // ← ИСПРАВЛЕНО: было Long, стало Integer
    private LocalDate bookdate;
    private String figi;
    private String name;
    private String isin;
    private Integer priority;
    private BigDecimal buyPrice;
    private Integer buyQuantity;
    private BigDecimal sellPrice;
    private Integer sellQuantity;

    // Конструкторы
    public InstrumentDTO() {
        this.bookdate = LocalDate.now();
        this.priority = 1;
    }

    // Геттеры и сеттеры
    public Integer getId() {  // ← ИСПРАВЛЕНО: было Long, стало Integer
        return id;
    }

    public void setId(Integer id) {  // ← ИСПРАВЛЕНО: было Long, стало Integer
        this.id = id;
    }

    public LocalDate getBookdate() {
        return bookdate;
    }

    public void setBookdate(LocalDate bookdate) {
        this.bookdate = bookdate;
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

    @Override
    public String toString() {
        return "InstrumentDTO{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", isin='" + isin + '\'' +
                ", priority=" + priority +
                '}';
    }
}
