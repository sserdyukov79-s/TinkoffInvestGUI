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
    public static final int DIALOG_WIDTH = 500;
    public static final int DIALOG_HEIGHT = 450;
    public static final int JSON_DIALOG_WIDTH = 800;
    public static final int JSON_DIALOG_HEIGHT = 600;

    // ===== ШРИФТЫ =====
    public static final String FONT_ARIAL = "Arial";
    public static final String FONT_MONOSPACED = "Monospaced";
    public static final int FONT_SIZE_TITLE = 18;
    public static final int FONT_SIZE_SUBTITLE = 14;
    public static final int FONT_SIZE_NORMAL = 12;
    public static final int FONT_SIZE_SMALL = 10;

    // ===== ЦВЕТА =====
    public static final int COLOR_WARNING_RED = 0xE74C3C; // #E74C3C
    public static final int COLOR_SUCCESS_GREEN = 0x2ECC71; // #2ECC71
    public static final int COLOR_INFO_BLUE = 0x3498DB; // #3498DB

    // ===== ТАЙМАУТЫ И ИНТЕРВАЛЫ =====
    public static final long PORTFOLIO_UPDATE_INTERVAL_MINUTES = 5;
    public static final int ORDERS_DELAY_MILLIS = 500;
    public static final int GRPC_CONNECTION_TIMEOUT_SECONDS = 30;
    public static final int ORDERSDELAYMILLIS = 500;

    // ===== SQL ЗАПРОСЫ =====
    public static final String SQL_SELECT_ALL_INSTRUMENTS =
    "SELECT id, priority, figi, name, isin,  " +
            "buy_quantity, buy_price, manual_buy_price, sell_quantity, sell_price, manual_sell_price " +
            "FROM public.instruments " +
            "WHERE bookdate = CURRENT_DATE " +
            "ORDER BY priority, name";

    public static final String SQL_SELECT_INSTRUMENTS_BY_DATE =
            "SELECT * FROM public.instruments WHERE bookdate = ? ORDER BY priority, name";

    public static final String SQL_INSERT_INSTRUMENT =
            "INSERT INTO public.instruments (bookdate, figi, name, isin, priority, " +
                    "buy_price, buy_quantity, sell_price, sell_quantity, manual_buy_price, manual_sell_price) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String SQL_UPDATE_INSTRUMENT =
            "UPDATE public.instruments SET bookdate = ?, figi = ?, name = ?, isin = ?, priority = ?, " +
                    "buy_price = ?, buy_quantity = ?, sell_price = ?, sell_quantity = ?, " +
                    "manual_buy_price = ?, manual_sell_price = ? WHERE id = ?";

    public static final String SQL_DELETE_INSTRUMENT =
            "DELETE FROM public.instruments WHERE id = ?";

    public static final String SQL_COUNT_INSTRUMENTS =
            "SELECT COUNT(*) FROM public.instruments";

    public static final String SQL_GET_LATEST_BOOKDATE =
            "SELECT MAX(bookdate) FROM public.instruments";


    // ===== СООБЩЕНИЯ =====
    public static final String MSG_NO_INSTRUMENTS = "Нет инструментов для формирования заявок";
    public static final String MSG_NO_ACCOUNT_SELECTED = "Не выбран счёт. Перейдите на вкладку 'Портфель и счета' и загрузите счета.";
    public static final String MSG_ORDERS_SENT_SUCCESS = "✅ Заявки отправлены!\n\nПроверьте логи для детальной информации.\nВсе JSON-запросы записаны в лог-файл.";
    public static final String MSG_CONFIRM_DELETE = "Удалить инструмент \"%s\"?";
    public static final String MSG_INSTRUMENT_ADDED = "✓ Инструмент добавлен!";
    public static final String MSG_INSTRUMENT_UPDATED = "✓ Инструмент обновлён!";
    public static final String MSG_INSTRUMENT_DELETED = "✓ Инструмент удалён!";

    // ===== ЗАГОЛОВКИ ТАБЛИЦ =====
    public static final String[] INSTRUMENTS_TABLE_COLUMNS = {
            "ID", "Приоритет", "FIGI", "Название", "ISIN",
             "Кол-во покупки", "Цена покупки", "Моя цена покупки",
            "Кол-во продажи", "Цена продажи", "Моя цена продажи"
    };

    public static final String[] ACCOUNTS_TABLE_COLUMNS = {
            "ID", "Название", "Тип", "Статус"
    };

    public static final String[] PORTFOLIO_TABLE_COLUMNS = {
            "FIGI", "Тикер", "Тип", "Площадка", "Кол-во", "Средняя цена", "Стоимость"
    };

    // ===== VALIDATION =====
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 100;
    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 1000000;
}
