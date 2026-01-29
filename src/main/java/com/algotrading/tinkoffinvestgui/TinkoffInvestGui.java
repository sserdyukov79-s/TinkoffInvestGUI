package com.algotrading.tinkoffinvestgui;

import com.algotrading.tinkoffinvestgui.api.AccountsService;
import com.algotrading.tinkoffinvestgui.api.PortfolioService;
import com.algotrading.tinkoffinvestgui.api.BondsService;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.repository.BondsRepository;
import ru.tinkoff.piapi.contract.v1.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class TinkoffInvestGui extends JFrame {

    private JLabel accountsLabel;
    private JTable accountsTable;
    private JTable portfolioTable;
    private JTable bondsTable;
    private JButton refreshButton;
    private JButton portfolioButton;
    private JButton bondsButton;
    private String selectedAccountId = "";
    private ScheduledExecutorService portfolioUpdateExecutor;
    private static final long PORTFOLIO_UPDATE_INTERVAL_MINUTES = 5;

    public TinkoffInvestGui() {
        setTitle("Tinkoff Invest Accounts");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setSize(1400, 900);
        setLocationRelativeTo(null);

        JLabel title = new JLabel("🧾 Tinkoff Invest - Портфолио и счета", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));

        accountsLabel = new JLabel("Счета: --");
        accountsLabel.setFont(new Font("Arial", Font.BOLD, 14));

        refreshButton = new JButton("🔄 Обновить счета");
        refreshButton.addActionListener(e -> updateAccounts());

        portfolioButton = new JButton("💼 Загрузить портфель");
        portfolioButton.addActionListener(e -> showPortfolio());

        bondsButton = new JButton("💾 Экспорт облигаций в БД");
        bondsButton.addActionListener(e -> exportBondsToDatabase());

        String[] accountColumns = {"ID", "Название", "Тип", "Статус"};
        accountsTable = new JTable(new DefaultTableModel(new Object[][]{{"Загрузка..."}}, accountColumns));

        String[] portfolioColumns = {"FIGI", "Тикер", "Тип", "Площадка", "Кол-во", "Средняя цена", "Стоимость"};
        portfolioTable = new JTable(new DefaultTableModel(new Object[][]{{"--"}}, portfolioColumns));

        String[] bondsColumns = {"FIGI", "Ticker", "Class Code", "ISIN", "Название", "Валюта инструмента", "Валюта номинала", "Номинал", "Дата погашения", "DlongClient", "Плавающий купон", "Амортизация", "Риск"};
        bondsTable = new JTable(new DefaultTableModel(new Object[][]{{"--"}}, bondsColumns));

        JScrollPane accountsScroll = new JScrollPane(accountsTable);
        JScrollPane portfolioScroll = new JScrollPane(portfolioTable);
        JScrollPane bondsScroll = new JScrollPane(bondsTable);

        // Верхняя панель с заголовком и кнопками
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        topPanel.add(title);
        topPanel.add(Box.createVerticalStrut(10));

        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statsPanel.add(accountsLabel);
        topPanel.add(statsPanel);

        topPanel.add(Box.createVerticalStrut(10));
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonsPanel.add(refreshButton);
        buttonsPanel.add(portfolioButton);
        buttonsPanel.add(bondsButton);
        topPanel.add(buttonsPanel);

        // Центральная панель с таблицами
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel accountsTableLabel = new JLabel("📊 Мои счета:");
        accountsTableLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(accountsTableLabel);
        centerPanel.add(accountsScroll);
        centerPanel.add(Box.createVerticalStrut(10));

        JLabel portfolioLabel = new JLabel("💼 Портфель:");
        portfolioLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(portfolioLabel);
        centerPanel.add(portfolioScroll);
        centerPanel.add(Box.createVerticalStrut(10));

        JLabel bondsLabel = new JLabel("🔗 Облигации:");
        bondsLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(bondsLabel);
        centerPanel.add(bondsScroll);

        // Добавляем панели на форму
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        // Запускаем автообновление портфеля
        startPortfolioAutoUpdate();

        updateAccounts();
    }

    /**
     * Запускает автообновление портфеля каждые 5 минут
     */
    private void startPortfolioAutoUpdate() {
        portfolioUpdateExecutor = Executors.newScheduledThreadPool(1);
        portfolioUpdateExecutor.scheduleAtFixedRate(
                this::showPortfolio,
                PORTFOLIO_UPDATE_INTERVAL_MINUTES,
                PORTFOLIO_UPDATE_INTERVAL_MINUTES,
                java.util.concurrent.TimeUnit.MINUTES
        );
    }

    private void updateAccounts() {
        refreshButton.setEnabled(false);
        refreshButton.setText("⏳ Обновление...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    AccountsService service = new AccountsService();
                    int count = service.getAccountsCount();
                    GetAccountsResponse accounts = service.getAccounts();

                    SwingUtilities.invokeLater(() -> {
                        accountsLabel.setText("Счета: " + count);
                        updateAccountsTable(accountsTable, accounts.getAccountsList());

                        // Выбираем первый счет для портфеля по умолчанию
                        if (!accounts.getAccountsList().isEmpty()) {
                            selectedAccountId = accounts.getAccountsList().get(0).getId();
                            System.out.println("✓ Выбран счет по умолчанию: " + selectedAccountId);
                        }
                    });

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(TinkoffInvestGui.this,
                                    "Ошибка загрузки счетов: " + e.getMessage(),
                                    "Ошибка", JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                refreshButton.setText("🔄 Обновить счета");
            }
        };

        worker.execute();
    }

    private void showPortfolio() {
        if (selectedAccountId == null || selectedAccountId.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Сначала загрузи счета", "Внимание", JOptionPane.WARNING_MESSAGE);
            return;
        }

        portfolioButton.setEnabled(false);
        portfolioButton.setText("⏳ Загрузка портфеля...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    PortfolioService service = new PortfolioService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT
                    );

                    PortfolioResponse portfolio = service.getPortfolio(selectedAccountId);
                    SwingUtilities.invokeLater(() -> updatePortfolioTable(portfolio));

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(TinkoffInvestGui.this,
                                    "Ошибка загрузки портфеля: " + e.getMessage(),
                                    "Ошибка", JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }

            @Override
            protected void done() {
                portfolioButton.setEnabled(true);
                portfolioButton.setText("💼 Загрузить портфель");
            }
        };

        worker.execute();
    }

    /**
     * Экспортирует облигации в БД (таблица public.exportdata)
     */
    private void exportBondsToDatabase() {
        bondsButton.setEnabled(false);
        bondsButton.setText("⏳ Экспорт в БД...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    // 1. Получаем облигации из API
                    BondsService bondsService = new BondsService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT
                    );

                    System.out.println("📡 Запрашиваю облигации из API...");
                    BondsResponse response = bondsService.getBonds();
                    java.util.List<Bond> bonds = response.getInstrumentsList();
                    System.out.println("✓ Получено облигаций: " + bonds.size());

                    // 2. Экспортируем в БД
                    BondsRepository repository = new BondsRepository();
                    int exportedCount = repository.exportBonds(bonds);

                    // 3. Проверяем количество строк в БД
                    int totalRows = repository.getRowCount();

                    // 4. Обновляем GUI таблицу
                    SwingUtilities.invokeLater(() -> {
                        updateBondsTable(bonds);
                        JOptionPane.showMessageDialog(TinkoffInvestGui.this,
                                "✓ Экспорт завершён!\n" +
                                        "Таблица: public.exportdata\n" +
                                        "Облигаций: " + exportedCount + "\n" +
                                        "Всего строк (с заголовком): " + totalRows,
                                "Успех", JOptionPane.INFORMATION_MESSAGE);
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(TinkoffInvestGui.this,
                                    "❌ Ошибка экспорта: " + e.getMessage(),
                                    "Ошибка", JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }

            @Override
            protected void done() {
                bondsButton.setEnabled(true);
                bondsButton.setText("💾 Экспорт облигаций в БД");
            }
        };

        worker.execute();
    }

    /**
     * Обновляет таблицу облигаций (пока оставляем для визуализации)
     */
    private void updateBondsTable(java.util.List<Bond> bonds) {
        if (bonds.isEmpty()) {
            bondsTable.setModel(new DefaultTableModel(
                    new Object[][]{{"Нет облигаций"}},
                    new String[]{"Информация"}));
            return;
        }

        int rowCount = Math.min(bonds.size(), 100);
        Object[][] data = new Object[rowCount][13];

        for (int i = 0; i < rowCount; i++) {
            Bond bond = bonds.get(i);

            data[i][0] = bond.getFigi();
            data[i][1] = bond.getTicker();
            data[i][2] = bond.getClassCode();
            data[i][3] = bond.getIsin();
            data[i][4] = bond.getName();
            data[i][5] = bond.getCurrency().toUpperCase();

            if (bond.hasInitialNominal()) {
                data[i][6] = bond.getInitialNominal().getCurrency().toUpperCase();
            } else {
                data[i][6] = "--";
            }

            if (bond.hasInitialNominal()) {
                double nominal = bond.getInitialNominal().getUnits() +
                        bond.getInitialNominal().getNano() / 1e9;
                data[i][7] = String.format("%.0f", nominal);
            } else {
                data[i][7] = "--";
            }

            if (bond.hasMaturityDate()) {
                long seconds = bond.getMaturityDate().getSeconds();
                java.time.LocalDate date = java.time.LocalDateTime
                        .ofEpochSecond(seconds, 0, java.time.ZoneOffset.UTC)
                        .toLocalDate();
                data[i][8] = date.toString();
            } else {
                data[i][8] = "--";
            }

            if (bond.hasDlongMin()) {
                double dlongClient = bond.getDlongMin().getUnits() +
                        bond.getDlongMin().getNano() / 1e9;
                data[i][9] = String.format("%.2f", dlongClient);
            } else {
                data[i][9] = "0";
            }

            data[i][10] = bond.getFloatingCouponFlag() ? "Да" : "Нет";
            data[i][11] = bond.getAmortizationFlag() ? "Да" : "Нет";

            String risk = formatRiskLevel(bond.getRiskLevel());
            data[i][12] = risk;
        }

        bondsTable.setModel(new DefaultTableModel(data,
                new String[]{"FIGI", "Ticker", "Class Code", "ISIN", "Название", "Валюта инструмента",
                        "Валюта номинала", "Номинал", "Дата погашения", "DlongClient",
                        "Плавающий купон", "Амортизация", "Риск"}));

        System.out.println("✓ Таблица облигаций обновлена (" + rowCount + " записей)");
    }

    private String formatRiskLevel(RiskLevel riskLevel) {
        switch (riskLevel) {
            case RISK_LEVEL_LOW: return "Низкий";
            case RISK_LEVEL_MODERATE: return "Средний";
            case RISK_LEVEL_HIGH: return "Высокий";
            default: return riskLevel.name();
        }
    }

    private void updateAccountsTable(JTable table, java.util.List<Account> accounts) {
        if (accounts.isEmpty()) {
            table.setModel(new DefaultTableModel(new Object[][]{{"Нет счетов"}}, new String[]{"Информация"}));
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
    }

    private void updatePortfolioTable(PortfolioResponse portfolio) {
        if (portfolio.getPositionsCount() == 0) {
            portfolioTable.setModel(new DefaultTableModel(
                    new Object[][]{{"Нет позиций"}},
                    new String[]{"Информация"}));
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
            double price = position.getAveragePositionPrice().getUnits() +
                    position.getAveragePositionPrice().getNano() / 1e9;
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
                new String[]{"FIGI", "Тикер", "Тип", "Площадка", "Кол-во", "Средняя цена", "Стоимость"}));
    }

    private String formatAccountType(AccountType type) {
        switch (type) {
            case ACCOUNT_TYPE_TINKOFF: return "Брокерский";
            case ACCOUNT_TYPE_TINKOFF_IIS: return "ИИС";
            case ACCOUNT_TYPE_INVEST_BOX: return "Инвесткопилка";
            default: return type.name();
        }
    }

    private String formatAccountStatus(AccountStatus status) {
        switch (status) {
            case ACCOUNT_STATUS_OPEN: return "Открыт ✓";
            case ACCOUNT_STATUS_CLOSED: return "Закрыт ✗";
            default: return status.name();
        }
    }

    @Override
    public void addWindowListener(java.awt.event.WindowListener l) {
        super.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopPortfolioAutoUpdate();
                System.exit(0);
            }
        });
        super.addWindowListener(l);
    }

    private void stopPortfolioAutoUpdate() {
        if (portfolioUpdateExecutor != null && !portfolioUpdateExecutor.isShutdown()) {
            portfolioUpdateExecutor.shutdown();
            try {
                if (!portfolioUpdateExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    portfolioUpdateExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                portfolioUpdateExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            new TinkoffInvestGui().setVisible(true);
        });
    }
}
