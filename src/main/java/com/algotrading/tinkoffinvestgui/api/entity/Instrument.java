package com.algotrading.tinkoffinvestgui.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entity для работы с инструментами из таблицы instruments
 */
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

    // Конструкторы
    public Instrument() {
        this.bookdate = LocalDate.now(); // По умолчанию текущая дата
    }

    public Instrument(String figi, String name, String isin, Integer priority) {
        this();
        this.figi = figi;
        this.name = name;
        this.isin = isin;
        this.priority = priority;
    }

    // Getters и Setters
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
