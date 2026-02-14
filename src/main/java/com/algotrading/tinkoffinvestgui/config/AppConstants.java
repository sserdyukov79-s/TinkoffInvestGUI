package com.algotrading.tinkoffinvestgui.config;

/**
 * Константы приложения
 */
public final class AppConstants {

    private AppConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ===== GUI КОНСТАНТЫ =====
    public static final int WINDOW_WIDTH = 1400;
    public static final int WINDOW_HEIGHT = 850;

    public static final int ORDERSDELAYMILLIS = 500;

    // ===== SQL ЗАПРОСЫ =====
    public static final String SQL_SELECT_ALL_INSTRUMENTS =
            "SELECT id, priority, figi, name, isin, " +
                    "       buy_quantity, buy_price, manual_buy_price, " +
                    "       sell_quantity, sell_price, manual_sell_price, " +
                    "       sell_price_fixed, sell_price_fixed_date " +
                    "FROM public.instruments " +
                    "WHERE bookdate = CURRENT_DATE " +
                    "ORDER BY priority, name";



    // ===== ЗАГОЛОВКИ ТАБЛИЦ =====
    public static final String[] INSTRUMENTS_TABLE_COLUMNS = {
            "ID", "Приоритет", "FIGI", "Название", "ISIN",
             "Кол-во покупки", "Цена покупки", "Моя цена покупки",
            "Кол-во продажи", "Цена продажи", "Моя цена продажи"
    };



    // ===== VALIDATION =====
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 100;
    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 1000000;
}
