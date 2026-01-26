package com.algotrading.tinkoffinvestgui.ui;

import ru.tinkoff.piapi.contract.v1.*;
import javax.swing.table.DefaultTableModel;

/**
 * Форматирует данные портфеля для отображения в таблице.
 * Отделяет логику форматирования от UI.
 */
public class PortfolioTableFormatter {

    /**
     * Подготавливает данные портфеля для таблицы
     */
    public static Object[][] formatPortfolioData(PortfolioResponse portfolio) {
        if (portfolio.getPositionsCount() == 0) {
            return new Object[][]{};
        }

        Object[][] data = new Object[portfolio.getPositionsCount()][7];

        for (int i = 0; i < portfolio.getPositionsCount(); i++) {
            PortfolioPosition position = portfolio.getPositions(i);
            data[i] = formatPositionRow(position);
        }

        return data;
    }

    /**
     * Форматирует одну позицию в строку таблицы
     */
    private static Object[] formatPositionRow(PortfolioPosition position) {
        String figi = extractFigi(position);
        String ticker = extractTicker(position);
        String type = formatInstrumentType(position);
        String classCode = extractClassCode(position);
        String quantity = formatQuantity(position.getQuantity());
        String avgPrice = formatPrice(position.getAveragePositionPrice());
        String totalCost = calculateTotalCost(position);

        return new Object[]{figi, ticker, type, classCode, quantity, avgPrice, totalCost};
    }

    /**
     * Извлекает и форматирует FIGI
     */
    private static String extractFigi(PortfolioPosition position) {
        return (position != null && !position.getFigi().isEmpty())
                ? position.getFigi()
                : "--";
    }

    /**
     * Извлекает и форматирует тикер
     */
    private static String extractTicker(PortfolioPosition position) {
        return (position != null && !position.getTicker().isEmpty())
                ? position.getTicker()
                : "--";
    }

    /**
     * Извлекает и форматирует код площадки
     */
    private static String extractClassCode(PortfolioPosition position) {
        return (position != null && !position.getClassCode().isEmpty())
                ? position.getClassCode()
                : "--";
    }

    /**
     * Форматирует тип инструмента
     */
    private static String formatInstrumentType(PortfolioPosition position) {
        if (position == null || position.getInstrumentType().isEmpty()) {
            return "--";
        }

        String type = position.getInstrumentType();
        switch (type) {
            case "share": return "Акция";
            case "bond": return "Облигация";
            case "etf": return "ETF";
            case "currency": return "Валюта";
            case "futures": return "Фьючерс";
            case "option": return "Опцион";
            default: return type;
        }
    }

    /**
     * Форматирует количество (units + nano)
     */
    private static String formatQuantity(Quotation quantity) {
        if (quantity == null) return "0";
        return String.format("%.4f", quantity.getUnits() + quantity.getNano() / 1e9);
    }

    /**
     * Форматирует цену в рубли
     */
    private static String formatPrice(MoneyValue price) {
        if (price == null) return "0 ₽";
        double rubles = price.getUnits() + price.getNano() / 1e9;
        return String.format("%.2f ₽", rubles);
    }

    /**
     * Рассчитывает общую стоимость позиции
     */
    private static String calculateTotalCost(PortfolioPosition position) {
        if (position == null) return "0 ₽";

        double qty = position.getQuantity().getUnits() + position.getQuantity().getNano() / 1e9;
        double price = position.getAveragePositionPrice().getUnits() +
                position.getAveragePositionPrice().getNano() / 1e9;
        double totalCost = qty * price;

        return String.format("%.2f ₽", totalCost);
    }

    /**
     * Получает заголовки колонок таблицы портфеля
     */
    public static String[] getPortfolioColumnHeaders() {
        return new String[]{"FIGI", "Тикер", "Тип", "Площадка", "Кол-во", "Средняя цена", "Стоимость"};
    }

    /**
     * Проверяет, пуста ли таблица портфеля
     */
    public static boolean isPortfolioEmpty(PortfolioResponse portfolio) {
        return portfolio == null || portfolio.getPositionsCount() == 0;
    }
}
