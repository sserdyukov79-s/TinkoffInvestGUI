package com.algotrading.tinkoffinvestgui.ui.panels;

import com.algotrading.tinkoffinvestgui.api.AccountsApiService;
import com.algotrading.tinkoffinvestgui.api.PortfolioService;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.ui.utils.AsyncTask;
import com.algotrading.tinkoffinvestgui.ui.utils.DialogUtils;
import com.algotrading.tinkoffinvestgui.ui.utils.TableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Панель портфеля
 */
public class PortfolioPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(PortfolioPanel.class);
    private static final long PORTFOLIO_UPDATE_INTERVAL_MINUTES = 5;

    private final JFrame parentFrame;

    // UI компоненты
    private JLabel accountsLabel;
    private JTable accountsTable;
    private JTable portfolioTable;
    private JButton refreshButton;
    private JButton portfolioButton;

    private ScheduledExecutorService portfolioUpdateExecutor;

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

        buttonsPanel.add(refreshButton);
        buttonsPanel.add(portfolioButton);

        // Таблицы
        String[] accountColumns = {"ID", "Название", "Тип", "Статус"};
        accountsTable = new JTable(new DefaultTableModel(new Object[][]{}, accountColumns));
        TableUtils.addCopyMenu(accountsTable);

        String[] portfolioColumns = {"FIGI", "Тикер", "Тип", "Класс", "Кол-во", "Средняя цена", "Общая стоимость"};
        portfolioTable = new JTable(new DefaultTableModel(new Object[][]{}, portfolioColumns));
        TableUtils.addCopyMenu(portfolioTable);

        JScrollPane accountsScroll = new JScrollPane(accountsTable);
        JScrollPane portfolioScroll = new JScrollPane(portfolioTable);

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

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
    }

    /**
     * Запуск автоматического обновления портфеля
     */
    public void startAutoUpdate() {
        log.info("⏰ Запуск автоматического обновления портфеля каждые {} минут", PORTFOLIO_UPDATE_INTERVAL_MINUTES);
        portfolioUpdateExecutor = Executors.newScheduledThreadPool(1);
        portfolioUpdateExecutor.scheduleAtFixedRate(
                this::showPortfolio,
                PORTFOLIO_UPDATE_INTERVAL_MINUTES,
                PORTFOLIO_UPDATE_INTERVAL_MINUTES,
                TimeUnit.MINUTES
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
    }

    /**
     * Обновление счетов и портфеля
     */
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
                },
                error -> {
                    log.error("❌ Ошибка обновления счетов", error);
                    DialogUtils.showError(parentFrame, error.getMessage());
                    refreshButton.setEnabled(true);
                    refreshButton.setText("Обновить счета");
                }
        );
    }

    /**
     * Обновление только счетов
     */
    private void updateAccounts() {
        updateAccountsAndPortfolio();
    }

    /**
     * Получение и обновление портфеля
     */
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
     * Обновление таблицы счетов
     */
    private void updateAccountsTable(JTable table, java.util.List<Account> accounts) {
        if (accounts.isEmpty()) {
            log.warn("⚠️ Нет счетов для отображения");
            table.setModel(new DefaultTableModel(new Object[][]{}, new String[]{}));
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
        log.debug("🔄 Таблица счетов обновлена, строк: {}, счетов: {}", data.length, accounts.size());
    }

    /**
     * Обновление таблицы портфеля
     */
    private void updatePortfolioTable(PortfolioResponse portfolio) {
        if (portfolio.getPositionsCount() == 0) {
            log.warn("⚠️ Нет позиций в портфеле");
            portfolioTable.setModel(new DefaultTableModel(new Object[][]{}, new String[]{}));
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
        log.debug("🔄 Портфель обновлён, строк: {}, позиций: {}", data.length, portfolio.getPositionsCount());
    }

    /**
     * Форматирование типа счёта
     */
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

    /**
     * Форматирование статуса счёта
     */
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
