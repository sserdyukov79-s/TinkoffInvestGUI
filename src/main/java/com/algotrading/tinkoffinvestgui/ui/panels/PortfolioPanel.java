package com.algotrading.tinkoffinvestgui.ui.panels;

import com.algotrading.tinkoffinvestgui.api.AccountsApiService;
import com.algotrading.tinkoffinvestgui.api.PortfolioService;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.model.Order;
import com.algotrading.tinkoffinvestgui.repository.OrdersRepository;
import com.algotrading.tinkoffinvestgui.service.AccountService;
import com.algotrading.tinkoffinvestgui.service.TinkoffApiService;
import com.algotrading.tinkoffinvestgui.ui.utils.AsyncTask;
import com.algotrading.tinkoffinvestgui.ui.utils.DialogUtils;
import com.algotrading.tinkoffinvestgui.ui.utils.TableUtils;
import com.algotrading.tinkoffinvestgui.repository.TradesRepository;
import com.algotrading.tinkoffinvestgui.service.TradesSyncService;
import com.algotrading.tinkoffinvestgui.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Панель портфеля
 */
public class PortfolioPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(PortfolioPanel.class);
    private static final long PORTFOLIO_UPDATE_INTERVAL_MINUTES = 5;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JFrame parentFrame;

    // UI компоненты
    private JLabel accountsLabel;
    private JTable accountsTable;
    private JScrollPane accountsScroll;
    private JTable portfolioTable;
    private JScrollPane portfolioScroll;
    private JTable ordersTable;
    private JScrollPane ordersScroll;
    private JButton refreshButton;
    private JButton portfolioButton;
    private JButton ordersButton;
    private JTable tradesTable;
    private JScrollPane tradesScroll;
    private JButton tradesButton;
    private ScheduledExecutorService tradesSyncExecutor;

    private ScheduledExecutorService portfolioUpdateExecutor;
    private ScheduledExecutorService orderTrackerExecutor;  // >>> НОВЫЙ EXECUTOR ДЛЯ ТРЕКЕРА

    private final OrdersRepository ordersRepository = new OrdersRepository();
    private final TradesRepository tradesRepository = new TradesRepository();
    private final TradesSyncService tradesSyncService = new TradesSyncService();

    public PortfolioPanel(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Заголовок
        JLabel title = new JLabel("Портфель Tinkoff Invest", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));

        JLabel accountsInfoLabel = new JLabel("Информация о счетах. Account ID берётся из parameters.account1");
        accountsInfoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        accountsInfoLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));

        accountsLabel = new JLabel("Счета: --");
        accountsLabel.setFont(new Font("Arial", Font.BOLD, 14));

        // Кнопки
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        refreshButton = new JButton("Обновить счета");
        refreshButton.addActionListener(e -> updateAccounts());

        portfolioButton = new JButton("Обновить портфель");
        portfolioButton.addActionListener(e -> showPortfolio());

        ordersButton = new JButton("Обновить заявки");
        ordersButton.addActionListener(e -> refreshOrders());

        tradesButton = new JButton("Обновить сделки");
        tradesButton.addActionListener(e -> refreshTrades());

        buttonsPanel.add(refreshButton);
        buttonsPanel.add(portfolioButton);
        buttonsPanel.add(ordersButton);
        buttonsPanel.add(tradesButton);

        // Таблицы
        String[] accountColumns = {"ID", "Название", "Тип", "Статус"};
        accountsTable = new JTable(new DefaultTableModel(new Object[][]{}, accountColumns));
        accountsTable.setFillsViewportHeight(false);
        TableUtils.addCopyMenu(accountsTable);

        String[] portfolioColumns = {"FIGI", "Тикер", "Тип", "Класс", "Кол-во", "Средняя цена", "Общая стоимость"};
        portfolioTable = new JTable(new DefaultTableModel(new Object[][]{}, portfolioColumns));
        portfolioTable.setFillsViewportHeight(false);
        TableUtils.addCopyMenu(portfolioTable);

        String[] ordersColumns = {
                "ID", "Инструмент", "Направление", "Кол-во", "Цена",
                "Исполнено", "Статус", "Создана", "Выставлена"
        };
        ordersTable = new JTable(new DefaultTableModel(new Object[][]{}, ordersColumns));
        ordersTable.setFillsViewportHeight(false);
        TableUtils.addCopyMenu(ordersTable);

        String[] tradesColumns = {
                "ID", "Инструмент", "Направление", "Кол-во", "Цена",
                "Сумма", "Комиссия", "НКД", "Дата сделки"
        };
        tradesTable = new JTable(new DefaultTableModel(new Object[][]{}, tradesColumns));
        tradesTable.setFillsViewportHeight(false);
        TableUtils.addCopyMenu(tradesTable);

        accountsScroll = new JScrollPane(accountsTable);
        portfolioScroll = new JScrollPane(portfolioTable);
        ordersScroll = new JScrollPane(ordersTable);
        tradesScroll = new JScrollPane(tradesTable);

        setTablePreferredHeight(accountsScroll, accountsTable, 3);
        setTablePreferredHeight(portfolioScroll, portfolioTable, 10);
        setTablePreferredHeight(ordersScroll, ordersTable, 8);
        setTablePreferredHeight(tradesScroll, tradesTable, 8);

        // Верхняя панель
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(title);
        topPanel.add(Box.createVerticalStrut(10));
        topPanel.add(accountsInfoLabel);
        topPanel.add(Box.createVerticalStrut(5));
        topPanel.add(accountsLabel);
        topPanel.add(Box.createVerticalStrut(10));
        topPanel.add(buttonsPanel);

        // Центральная панель
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        JLabel accountsTableLabel = new JLabel("Счета:");
        accountsTableLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(accountsTableLabel);
        centerPanel.add(accountsScroll);
        centerPanel.add(Box.createVerticalStrut(10));

        JLabel portfolioLabel = new JLabel("Позиции:");
        portfolioLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(portfolioLabel);
        centerPanel.add(portfolioScroll);

        centerPanel.add(Box.createVerticalStrut(15));
        JLabel ordersLabel = new JLabel("Активные заявки (сегодня):");
        ordersLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(ordersLabel);
        centerPanel.add(ordersScroll);

        centerPanel.add(Box.createVerticalStrut(15));
        JLabel tradesLabel = new JLabel("Сделки (сегодня):");
        tradesLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(tradesLabel);
        centerPanel.add(tradesScroll);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
    }


    private void setTablePreferredHeight(JScrollPane scrollPane, JTable table, int visibleRows) {
        int rowHeight = table.getRowHeight();
        int headerHeight = table.getTableHeader().getPreferredSize().height;
        int totalHeight = headerHeight + (rowHeight * visibleRows);

        scrollPane.setPreferredSize(new Dimension(
                scrollPane.getPreferredSize().width,
                totalHeight + 5
        ));
        scrollPane.setMaximumSize(new Dimension(
                Integer.MAX_VALUE,
                totalHeight + 5
        ));
    }

    private void adjustTableHeight(JScrollPane scrollPane, JTable table, int maxVisibleRows) {
        int actualRows = table.getRowCount();
        int visibleRows = Math.min(actualRows, maxVisibleRows);

        if (visibleRows == 0) {
            visibleRows = 2;
        }

        setTablePreferredHeight(scrollPane, table, visibleRows);
        scrollPane.revalidate();
    }

    /**
     * Запуск автоматического обновления портфеля и трекера заявок
     */
    public void startAutoUpdate() {
        log.info("⏰ Запуск автоматического обновления портфеля каждые {} минут", PORTFOLIO_UPDATE_INTERVAL_MINUTES);

        // Обновление портфеля
        portfolioUpdateExecutor = Executors.newScheduledThreadPool(1);
        portfolioUpdateExecutor.scheduleAtFixedRate(
                () -> {
                    showPortfolio();
                    refreshOrders();
                },
                PORTFOLIO_UPDATE_INTERVAL_MINUTES,
                PORTFOLIO_UPDATE_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );

        // Автоматический трекер статусов заявок каждые 30 секунд
        log.info("⏰ Запуск автоматической синхронизации статусов заявок каждые 30 секунд");
        orderTrackerExecutor = Executors.newScheduledThreadPool(1);
        orderTrackerExecutor.scheduleAtFixedRate(
                () -> {
                    syncOrderStatuses();
                    SwingUtilities.invokeLater(() -> {
                        if (ordersTable.getRowCount() > 0) {
                            refreshOrdersTableOnly();
                        }
                    });
                },
                10,
                30,
                TimeUnit.SECONDS
        );

        // >>> НОВОЕ: Автоматическая синхронизация сделок каждые 5 минут
        log.info("⏰ Запуск автоматической синхронизации сделок каждые 5 минут");
        tradesSyncExecutor = Executors.newScheduledThreadPool(1);
        tradesSyncExecutor.scheduleAtFixedRate(
                () -> {
                    syncTrades();
                    SwingUtilities.invokeLater(() -> {
                        if (tradesTable.getRowCount() > 0) {
                            refreshTradesTableOnly();
                        }
                    });
                },
                30,  // первый запуск через 30 сек
                300,  // затем каждые 5 минут (300 сек)
                TimeUnit.SECONDS
        );
    }


    /**
     * Остановка автоматического обновления
     */
    public void stopAutoUpdate() {
        if (portfolioUpdateExecutor != null && !portfolioUpdateExecutor.isShutdown()) {
            log.info("⏹️ Остановка автоматического обновления портфеля");
            portfolioUpdateExecutor.shutdown();
            try {
                if (!portfolioUpdateExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    portfolioUpdateExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                portfolioUpdateExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (orderTrackerExecutor != null && !orderTrackerExecutor.isShutdown()) {
            log.info("⏹️ Остановка трекера заявок");
            orderTrackerExecutor.shutdown();
            try {
                if (!orderTrackerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    orderTrackerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                orderTrackerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // >>> НОВОЕ: Остановка синхронизации сделок
        if (tradesSyncExecutor != null && !tradesSyncExecutor.isShutdown()) {
            log.info("⏹️ Остановка синхронизации сделок");
            tradesSyncExecutor.shutdown();
            try {
                if (!tradesSyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    tradesSyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                tradesSyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }


    public void updateAccountsAndPortfolio() {
        log.info("🔄 Обновление счетов и портфеля");
        refreshButton.setEnabled(false);
        refreshButton.setText("Загрузка...");

        AsyncTask.execute(
                () -> {
                    AccountsApiService service = new AccountsApiService();
                    int count = service.getAccountsCount();
                    GetAccountsResponse accounts = service.getAccounts();
                    log.info("✅ Получено счетов из API: {}", count);
                    return new Object[]{count, accounts};
                },
                result -> {
                    int count = (int) ((Object[]) result)[0];
                    GetAccountsResponse accounts = (GetAccountsResponse) ((Object[]) result)[1];

                    accountsLabel.setText("Счета: " + count);
                    updateAccountsTable(accountsTable, accounts.getAccountsList());

                    if (!accounts.getAccountsList().isEmpty()) {
                        String accountId = accounts.getAccountsList().get(0).getId();
                        PortfolioService portfolioService = new PortfolioService(
                                ConnectorConfig.getApiToken(),
                                ConnectorConfig.API_URL,
                                ConnectorConfig.API_PORT
                        );

                        AsyncTask.execute(
                                () -> portfolioService.getPortfolio(accountId),
                                portfolio -> {
                                    log.info("✅ Получен портфель, позиций: {}", portfolio.getPositionsCount());
                                    updatePortfolioTable(portfolio);
                                },
                                error -> log.error("❌ Ошибка получения портфеля", error)
                        );
                    }

                    refreshButton.setEnabled(true);
                    refreshButton.setText("Обновить счета");

                    refreshOrders();
                },
                error -> {
                    log.error("❌ Ошибка обновления счетов", error);
                    DialogUtils.showError(parentFrame, error.getMessage());
                    refreshButton.setEnabled(true);
                    refreshButton.setText("Обновить счета");
                }
        );
    }

    private void updateAccounts() {
        updateAccountsAndPortfolio();
    }

    private void showPortfolio() {
        if (accountsTable.getRowCount() == 0 || accountsTable.getValueAt(0, 0) == null) {
            log.warn("⚠️ Счета не загружены");
            return;
        }

        String displayAccountId = (String) accountsTable.getValueAt(0, 0);
        log.info("📊 Запрос портфеля для счёта: {}", displayAccountId);

        portfolioButton.setEnabled(false);
        portfolioButton.setText("Загрузка...");

        AsyncTask.execute(
                () -> {
                    PortfolioService service = new PortfolioService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT
                    );
                    return service.getPortfolio(displayAccountId);
                },
                portfolio -> {
                    log.info("✅ Получен портфель для {}, позиций: {}",
                            displayAccountId, portfolio.getPositionsCount());
                    updatePortfolioTable(portfolio);
                    portfolioButton.setEnabled(true);
                    portfolioButton.setText("Обновить портфель");
                },
                error -> {
                    log.error("❌ Ошибка получения портфеля для {}", displayAccountId, error);
                    DialogUtils.showError(parentFrame, error.getMessage());
                    portfolioButton.setEnabled(true);
                    portfolioButton.setText("Обновить портфель");
                }
        );
    }

    /**
     * >>> РЕШЕНИЕ 1: Обновление заявок с синхронизацией статусов
     */
    private void refreshOrders() {
        log.info("🔄 Обновление активных заявок");
        ordersButton.setEnabled(false);
        ordersButton.setText("Загрузка...");

        AsyncTask.execute(
                () -> {
                    // Сначала синхронизируем статусы с API
                    syncOrderStatuses();

                    // Потом получаем обновлённые данные из БД
                    return ordersRepository.findTodayOrders();
                },
                orders -> {
                    log.info("✅ Получено заявок из БД: {}", orders.size());
                    updateOrdersTable((List<Order>) orders);
                    ordersButton.setEnabled(true);
                    ordersButton.setText("Обновить заявки");
                },
                error -> {
                    log.error("❌ Ошибка получения заявок", error);
                    DialogUtils.showError(parentFrame, "Ошибка загрузки заявок: " + error.getMessage());
                    ordersButton.setEnabled(true);
                    ordersButton.setText("Обновить заявки");
                }
        );
    }

    /**
     * >>> НОВЫЙ МЕТОД: Обновление только таблицы заявок без блокировки кнопки
     */
    private void refreshOrdersTableOnly() {
        try {
            List<Order> orders = ordersRepository.findTodayOrders();
            updateOrdersTable(orders);
        } catch (Exception e) {
            log.error("Ошибка обновления таблицы заявок", e);
        }
    }

    /**
     * >>> РЕШЕНИЕ 1+2: Синхронизация статусов активных заявок с API
     */
    private void syncOrderStatuses() {
        try {
            String accountId;
            try {
                accountId = AccountService.getActiveAccountId();
            } catch (Exception e) {
                log.error("Не удалось получить account ID для синхронизации", e);
                return;
            }

            TinkoffApiService apiService = new TinkoffApiService(
                    ConnectorConfig.getApiToken(),
                    accountId
            );

            try {
                // Получаем активные заявки с биржи
                List<OrderState> apiOrders = apiService.getOrders();
                log.debug("📡 Получено активных заявок с биржи: {}", apiOrders.size());

                // Обновляем статусы в БД
                for (OrderState apiOrder : apiOrders) {
                    Order dbOrder = ordersRepository.findByExchangeOrderId(apiOrder.getOrderId());
                    if (dbOrder != null) {
                        String newStatus = apiOrder.getExecutionReportStatus().name()
                                .replace("EXECUTION_REPORT_STATUS_", "");

                        if (!newStatus.equals(dbOrder.getStatus())) {
                            dbOrder.setStatus(newStatus);
                            dbOrder.setLotsExecuted(apiOrder.getLotsExecuted());
                            ordersRepository.update(dbOrder);
                            log.info("✓ Обновлён статус заявки {}: {} → {}",
                                    dbOrder.getMyOrderId(), dbOrder.getStatus(), newStatus);
                        }
                    }
                }

                // Проверяем отменённые заявки (которых уже нет на бирже)
                List<Order> todayOrders = ordersRepository.findTodayOrders();
                for (Order dbOrder : todayOrders) {
                    if (dbOrder.getExchangeOrderId() == null) continue;

                    // Пропускаем уже завершённые заявки
                    if (dbOrder.getStatus().equals("FILLED") ||
                            dbOrder.getStatus().equals("CANCELLED") ||
                            dbOrder.getStatus().equals("REJECTED")) {
                        continue;
                    }

                    boolean existsOnExchange = apiOrders.stream()
                            .anyMatch(api -> api.getOrderId().equals(dbOrder.getExchangeOrderId()));

                    if (!existsOnExchange) {
                        log.info("⚠️ Заявка {} не найдена на бирже, помечаем как CANCELLED",
                                dbOrder.getMyOrderId());
                        dbOrder.setStatus("CANCELLED");
                        ordersRepository.update(dbOrder);
                    }
                }

            } finally {
                apiService.close();
            }
        } catch (Exception e) {
            log.error("Ошибка синхронизации статусов заявок", e);
        }
    }

    private void updateOrdersTable(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            log.warn("⚠️ Нет заявок для отображения");
            ordersTable.setModel(new DefaultTableModel(
                    new Object[][]{},
                    new String[]{"ID", "Инструмент", "Направление", "Кол-во", "Цена",
                            "Исполнено", "Статус", "Создана", "Выставлена"}
            ));
            adjustTableHeight(ordersScroll, ordersTable, 8);
            return;
        }

        Object[][] data = new Object[orders.size()][9];
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);

            data[i][0] = order.getId();
            data[i][1] = order.getInstrumentName() != null ? order.getInstrumentName() : order.getFigi();

            String direction = order.getDirection() != null ? order.getDirection().name() : "";
            direction = direction.replace("ORDER_DIRECTION_", "");
            data[i][2] = direction;

            data[i][3] = order.getLotsRequested();
            data[i][4] = order.getPrice() != null ? String.format("%.2f ₽", order.getPrice()) : "--";
            data[i][5] = order.getLotsExecuted();

            String status = order.getStatus() != null ? order.getStatus() : "UNKNOWN";
            status = status.replace("EXECUTION_REPORT_STATUS_", "");
            data[i][6] = status;

            data[i][7] = order.getCreatedAt() != null
                    ? order.getCreatedAt().atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)
                    : "--";

            data[i][8] = order.getSubmittedAt() != null
                    ? order.getSubmittedAt().atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)
                    : "--";
        }

        ordersTable.setModel(new DefaultTableModel(
                data,
                new String[]{"ID", "Инструмент", "Направление", "Кол-во", "Цена",
                        "Исполнено", "Статус", "Создана", "Выставлена"}
        ));
        adjustTableHeight(ordersScroll, ordersTable, 15);
        log.debug("🔄 Таблица заявок обновлена, строк: {}", data.length);
    }

    /**
     * Обновление сделок с синхронизацией через API
     */
    private void refreshTrades() {
        log.info("🔄 Обновление сделок");
        tradesButton.setEnabled(false);
        tradesButton.setText("Загрузка...");

        AsyncTask.execute(
                () -> {
                    try {
                        // Синхронизируем сделки через API
                        log.info("📡 Начало синхронизации сделок через API...");
                        tradesSyncService.syncTodayTrades();
                        log.info("✅ Синхронизация сделок завершена");

                        // Небольшая задержка для гарантии записи в БД
                        Thread.sleep(200);

                        // Получаем обновлённые данные из БД
                        List<Trade> trades = tradesRepository.findTodayTrades();
                        log.info("📊 Загружено сделок из БД для отображения: {}", trades.size());

                        return trades;
                    } catch (Exception e) {
                        log.error("❌ Ошибка в процессе обновления сделок", e);
                        throw new RuntimeException("Ошибка обновления: " + e.getMessage(), e);
                    }
                },
                trades -> {
                    List<Trade> tradesList = (List<Trade>) trades;
                    log.info("✅ Получено сделок из БД: {}", tradesList.size());

                    if (tradesList.isEmpty()) {
                        log.warn("⚠️ Список сделок пуст, но синхронизация прошла успешно");
                    }

                    updateTradesTable(tradesList);
                    tradesButton.setEnabled(true);
                    tradesButton.setText("Обновить сделки");
                },
                error -> {
                    log.error("❌ Ошибка получения сделок", error);
                    DialogUtils.showError(parentFrame, "Ошибка загрузки сделок: " + error.getMessage());
                    tradesButton.setEnabled(true);
                    tradesButton.setText("Обновить сделки");
                }
        );
    }


    /**
     * Обновление только таблицы сделок без блокировки кнопки
     */
    private void refreshTradesTableOnly() {
        try {
            List<Trade> trades = tradesRepository.findTodayTrades();
            updateTradesTable(trades);
        } catch (Exception e) {
            log.error("Ошибка обновления таблицы сделок", e);
        }
    }

    /**
     * Синхронизация сделок с API (фоновая задача)
     */
    private void syncTrades() {
        try {
            tradesSyncService.syncTodayTrades();
        } catch (Exception e) {
            log.error("Ошибка фоновой синхронизации сделок", e);
        }
    }

    /**
     * Обновление таблицы сделок
     */
    private void updateTradesTable(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            log.warn("⚠️ Нет сделок для отображения");
            tradesTable.setModel(new DefaultTableModel(
                    new Object[][]{},
                    new String[]{"ID", "Инструмент", "Направление", "Кол-во", "Цена",
                            "Сумма", "Комиссия", "НКД", "Дата сделки"}
            ));
            adjustTableHeight(tradesScroll, tradesTable, 8);
            return;
        }

        Object[][] data = new Object[trades.size()][9];
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);

            data[i][0] = trade.getId();
            data[i][1] = trade.getInstrumentName() != null
                    ? trade.getInstrumentName()
                    : trade.getFigi();

            String direction = trade.getDirection();
            direction = direction.replace("ORDER_DIRECTION_", "");
            data[i][2] = direction;

            data[i][3] = trade.getQuantity();
            data[i][4] = trade.getPrice() != null
                    ? String.format("%.2f ₽", trade.getPrice())
                    : "--";
            data[i][5] = trade.getTradeAmount() != null
                    ? String.format("%.2f ₽", trade.getTradeAmount())
                    : "--";
            data[i][6] = trade.getCommission() != null
                    ? String.format("%.2f ₽", trade.getCommission())
                    : "--";
            data[i][7] = trade.getAci() != null
                    ? String.format("%.2f ₽", trade.getAci())
                    : "--";

            data[i][8] = trade.getTradeDate() != null
                    ? trade.getTradeDate().atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)
                    : "--";
        }

        tradesTable.setModel(new DefaultTableModel(
                data,
                new String[]{"ID", "Инструмент", "Направление", "Кол-во", "Цена",
                        "Сумма", "Комиссия", "НКД", "Дата сделки"}
        ));
        adjustTableHeight(tradesScroll, tradesTable, 15);
        log.debug("🔄 Таблица сделок обновлена, строк: {}", data.length);
    }


    private void updateAccountsTable(JTable table, java.util.List<Account> accounts) {
        if (accounts.isEmpty()) {
            log.warn("⚠️ Нет счетов для отображения");
            table.setModel(new DefaultTableModel(new Object[][]{}, new String[]{}));
            adjustTableHeight(accountsScroll, accountsTable, 3);
            return;
        }

        Object[][] data = new Object[accounts.size()][4];
        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            data[i][0] = account.getId();
            data[i][1] = account.getName();
            data[i][2] = formatAccountType(account.getType());
            data[i][3] = formatAccountStatus(account.getStatus());
        }

        table.setModel(new DefaultTableModel(data, new String[]{"ID", "Название", "Тип", "Статус"}));
        adjustTableHeight(accountsScroll, accountsTable, 3);
        log.debug("🔄 Таблица счетов обновлена, строк: {}, счетов: {}", data.length, accounts.size());
    }

    private void updatePortfolioTable(PortfolioResponse portfolio) {
        if (portfolio.getPositionsCount() == 0) {
            log.warn("⚠️ Нет позиций в портфеле");
            portfolioTable.setModel(new DefaultTableModel(new Object[][]{}, new String[]{}));
            adjustTableHeight(portfolioScroll, portfolioTable, 10);
            return;
        }

        Object[][] data = new Object[portfolio.getPositionsCount()][7];
        for (int i = 0; i < portfolio.getPositionsCount(); i++) {
            PortfolioPosition position = portfolio.getPositions(i);
            String figi = PortfolioService.getFigi(position);
            String ticker = PortfolioService.getTicker(position);
            String instrumentType = PortfolioService.getInstrumentType(position);
            String classCode = PortfolioService.getClassCode(position);
            String quantity = PortfolioService.formatQuantity(position.getQuantity());
            String avgPrice = PortfolioService.formatPrice(position.getAveragePositionPrice());

            double qty = position.getQuantity().getUnits() + position.getQuantity().getNano() / 1e9;
            double price = position.getAveragePositionPrice().getUnits() + position.getAveragePositionPrice().getNano() / 1e9;
            double totalCost = qty * price;
            String cost = String.format("%.2f ₽", totalCost);

            data[i][0] = figi;
            data[i][1] = ticker;
            data[i][2] = instrumentType;
            data[i][3] = classCode;
            data[i][4] = quantity;
            data[i][5] = avgPrice;
            data[i][6] = cost;
        }

        portfolioTable.setModel(new DefaultTableModel(data,
                new String[]{"FIGI", "Тикер", "Тип", "Класс", "Кол-во", "Средняя цена", "Общая стоимость"}));
        adjustTableHeight(portfolioScroll, portfolioTable, 20);
        log.debug("🔄 Портфель обновлён, строк: {}, позиций: {}", data.length, portfolio.getPositionsCount());
    }

    private String formatAccountType(AccountType type) {
        switch (type) {
            case ACCOUNT_TYPE_TINKOFF:
                return "Брокерский";
            case ACCOUNT_TYPE_TINKOFF_IIS:
                return "ИИС";
            case ACCOUNT_TYPE_INVEST_BOX:
                return "Инвестбокс";
            default:
                return type.name();
        }
    }

    private String formatAccountStatus(AccountStatus status) {
        switch (status) {
            case ACCOUNT_STATUS_OPEN:
                return "Открыт";
            case ACCOUNT_STATUS_CLOSED:
                return "Закрыт";
            default:
                return status.name();
        }
    }
}
