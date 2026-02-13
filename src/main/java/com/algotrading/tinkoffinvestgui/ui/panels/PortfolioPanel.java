package com.algotrading.tinkoffinvestgui.ui.panels;

import com.algotrading.tinkoffinvestgui.api.AccountsApiService;
import com.algotrading.tinkoffinvestgui.api.PortfolioService;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.model.Order;
import com.algotrading.tinkoffinvestgui.repository.OrdersRepository;
import com.algotrading.tinkoffinvestgui.ui.utils.AsyncTask;
import com.algotrading.tinkoffinvestgui.ui.utils.DialogUtils;
import com.algotrading.tinkoffinvestgui.ui.utils.TableUtils;
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
    private JScrollPane accountsScroll;  // >>> ХРАНИМ ССЫЛКУ НА SCROLL PANE
    private JTable portfolioTable;
    private JScrollPane portfolioScroll;  // >>> ХРАНИМ ССЫЛКУ
    private JTable ordersTable;
    private JScrollPane ordersScroll;  // >>> ХРАНИМ ССЫЛКУ
    private JButton refreshButton;
    private JButton portfolioButton;
    private JButton ordersButton;

    private ScheduledExecutorService portfolioUpdateExecutor;

    private final OrdersRepository ordersRepository = new OrdersRepository();

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

        buttonsPanel.add(refreshButton);
        buttonsPanel.add(portfolioButton);
        buttonsPanel.add(ordersButton);

        // Таблицы
        String[] accountColumns = {"ID", "Название", "Тип", "Статус"};
        accountsTable = new JTable(new DefaultTableModel(new Object[][]{}, accountColumns));
        accountsTable.setFillsViewportHeight(false);  // >>> НЕ РАСТЯГИВАТЬ НА ВСЮ ВЫСОТУ
        TableUtils.addCopyMenu(accountsTable);

        String[] portfolioColumns = {"FIGI", "Тикер", "Тип", "Класс", "Кол-во", "Средняя цена", "Общая стоимость"};
        portfolioTable = new JTable(new DefaultTableModel(new Object[][]{}, portfolioColumns));
        portfolioTable.setFillsViewportHeight(false);  // >>> НЕ РАСТЯГИВАТЬ
        TableUtils.addCopyMenu(portfolioTable);

        String[] ordersColumns = {
                "ID", "Инструмент", "Направление", "Кол-во", "Цена",
                "Исполнено", "Статус", "Создана", "Выставлена"
        };
        ordersTable = new JTable(new DefaultTableModel(new Object[][]{}, ordersColumns));
        ordersTable.setFillsViewportHeight(false);  // >>> НЕ РАСТЯГИВАТЬ
        TableUtils.addCopyMenu(ordersTable);

        accountsScroll = new JScrollPane(accountsTable);
        portfolioScroll = new JScrollPane(portfolioTable);
        ordersScroll = new JScrollPane(ordersTable);

        // >>> УСТАНАВЛИВАЕМ НАЧАЛЬНЫЕ РАЗМЕРЫ
        setTablePreferredHeight(accountsScroll, accountsTable, 3);  // 3 строки для счетов
        setTablePreferredHeight(portfolioScroll, portfolioTable, 10);  // 10 строк для портфеля
        setTablePreferredHeight(ordersScroll, ordersTable, 8);  // 8 строк для заявок

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

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
    }

    /**
     * >>> НОВЫЙ МЕТОД: Устанавливает высоту ScrollPane под количество строк таблицы
     */
    private void setTablePreferredHeight(JScrollPane scrollPane, JTable table, int visibleRows) {
        int rowHeight = table.getRowHeight();
        int headerHeight = table.getTableHeader().getPreferredSize().height;
        int totalHeight = headerHeight + (rowHeight * visibleRows);

        scrollPane.setPreferredSize(new Dimension(
                scrollPane.getPreferredSize().width,
                totalHeight + 5  // +5px для границ
        ));
        scrollPane.setMaximumSize(new Dimension(
                Integer.MAX_VALUE,
                totalHeight + 5
        ));
    }

    /**
     * >>> НОВЫЙ МЕТОД: Автоматически подгоняет высоту ScrollPane под реальное количество строк
     */
    private void adjustTableHeight(JScrollPane scrollPane, JTable table, int maxVisibleRows) {
        int actualRows = table.getRowCount();
        int visibleRows = Math.min(actualRows, maxVisibleRows);

        // Если таблица пустая, показываем хотя бы 2 строки
        if (visibleRows == 0) {
            visibleRows = 2;
        }

        setTablePreferredHeight(scrollPane, table, visibleRows);
        scrollPane.revalidate();
    }

    public void startAutoUpdate() {
        log.info("⏰ Запуск автоматического обновления портфеля каждые {} минут", PORTFOLIO_UPDATE_INTERVAL_MINUTES);
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
    }

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

    private void refreshOrders() {
        log.info("🔄 Обновление активных заявок");
        ordersButton.setEnabled(false);
        ordersButton.setText("Загрузка...");

        AsyncTask.execute(
                () -> {
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

    private void updateOrdersTable(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            log.warn("⚠️ Нет заявок для отображения");
            ordersTable.setModel(new DefaultTableModel(
                    new Object[][]{},
                    new String[]{"ID", "Инструмент", "Направление", "Кол-во", "Цена",
                            "Исполнено", "Статус", "Создана", "Выставлена"}
            ));
            adjustTableHeight(ordersScroll, ordersTable, 8);  // >>> ПОДГОНЯЕМ РАЗМЕР
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
        adjustTableHeight(ordersScroll, ordersTable, 15);  // >>> ПОДГОНЯЕМ РАЗМЕР (макс. 15 строк)
        log.debug("🔄 Таблица заявок обновлена, строк: {}", data.length);
    }

    private void updateAccountsTable(JTable table, java.util.List<Account> accounts) {
        if (accounts.isEmpty()) {
            log.warn("⚠️ Нет счетов для отображения");
            table.setModel(new DefaultTableModel(new Object[][]{}, new String[]{}));
            adjustTableHeight(accountsScroll, accountsTable, 3);  // >>> ПОДГОНЯЕМ РАЗМЕР
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
        adjustTableHeight(accountsScroll, accountsTable, 3);  // >>> ПОДГОНЯЕМ РАЗМЕР (макс. 3 строки)
        log.debug("🔄 Таблица счетов обновлена, строк: {}, счетов: {}", data.length, accounts.size());
    }

    private void updatePortfolioTable(PortfolioResponse portfolio) {
        if (portfolio.getPositionsCount() == 0) {
            log.warn("⚠️ Нет позиций в портфеле");
            portfolioTable.setModel(new DefaultTableModel(new Object[][]{}, new String[]{}));
            adjustTableHeight(portfolioScroll, portfolioTable, 10);  // >>> ПОДГОНЯЕМ РАЗМЕР
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
        adjustTableHeight(portfolioScroll, portfolioTable, 20);  // >>> ПОДГОНЯЕМ РАЗМЕР (макс. 20 строк)
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
