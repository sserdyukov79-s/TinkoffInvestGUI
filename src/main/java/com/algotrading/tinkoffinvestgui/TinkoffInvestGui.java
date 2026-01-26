package com.algotrading.tinkoffinvestgui;

import com.algotrading.tinkoffinvestgui.api.AccountsService;
import com.algotrading.tinkoffinvestgui.api.GrpcChannelManager;
import com.algotrading.tinkoffinvestgui.api.PortfolioService;
import com.algotrading.tinkoffinvestgui.ui.PortfolioTableFormatter;
import ru.tinkoff.piapi.contract.v1.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class TinkoffInvestGui extends JFrame {
    private JLabel realAccountsLabel;
    private JLabel sandboxAccountsLabel;
    private JTable realAccountsTable;
    private JTable sandboxAccountsTable;
    private JTable portfolioTable;
    private JButton refreshButton;
    private JButton portfolioButton;
    private JComboBox<String> accountSelector;

    private String selectedAccountId;
    private ScheduledExecutorService portfolioUpdateExecutor;
    private static final long PORTFOLIO_UPDATE_INTERVAL_MINUTES = 5;

    public TinkoffInvestGui() {
        setTitle("Tinkoff Invest Accounts");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setSize(1000, 800);
        setLocationRelativeTo(null);

        JLabel title = new JLabel("🧾 Счета Tinkoff Invest", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));

        realAccountsLabel = new JLabel("Реальные счета: --");
        sandboxAccountsLabel = new JLabel("Sandbox счета: --");
        realAccountsLabel.setFont(new Font("Arial", Font.BOLD, 14));
        sandboxAccountsLabel.setFont(new Font("Arial", Font.BOLD, 14));

        refreshButton = new JButton("🔄 Обновить счета");
        refreshButton.addActionListener(e -> updateAccounts());

        portfolioButton = new JButton("💼 Портфель");
        portfolioButton.addActionListener(e -> showPortfolio());

        // Dropdown для выбора аккаунта
        accountSelector = new JComboBox<>();
        accountSelector.setFont(new Font("Arial", Font.PLAIN, 12));
        accountSelector.addActionListener(e -> {
            if (accountSelector.getSelectedIndex() > 0) {
                selectedAccountId = (String) accountSelector.getSelectedItem();
            }
        });

        String[] accountColumns = {"ID", "Название", "Тип", "Статус"};
        realAccountsTable = new JTable(new DefaultTableModel(new Object[][]{{"Загрузка..."}}, accountColumns));
        sandboxAccountsTable = new JTable(new DefaultTableModel(new Object[][]{{"Загрузка..."}}, accountColumns));

        String[] portfolioColumns = PortfolioTableFormatter.getPortfolioColumnHeaders();
        portfolioTable = new JTable(new DefaultTableModel(new Object[][]{{"--"}}, portfolioColumns));

        JScrollPane realScroll = new JScrollPane(realAccountsTable);
        JScrollPane sandboxScroll = new JScrollPane(sandboxAccountsTable);
        JScrollPane portfolioScroll = new JScrollPane(portfolioTable);

        // Верхняя панель с заголовком и кнопками
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        topPanel.add(title);
        topPanel.add(Box.createVerticalStrut(10));

        JPanel statsPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        statsPanel.add(realAccountsLabel);
        statsPanel.add(sandboxAccountsLabel);
        topPanel.add(statsPanel);

        topPanel.add(Box.createVerticalStrut(10));

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonsPanel.add(refreshButton);
        buttonsPanel.add(portfolioButton);
        buttonsPanel.add(new JLabel("Выбрать счет:"));
        buttonsPanel.add(accountSelector);
        topPanel.add(buttonsPanel);

        // Центральная панель с таблицами
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel realLabel = new JLabel("📊 Реальные счета:");
        realLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(realLabel);
        centerPanel.add(realScroll);
        centerPanel.add(Box.createVerticalStrut(10));

        JLabel sandboxLabel = new JLabel("🏖️ Sandbox счета:");
        sandboxLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(sandboxLabel);
        centerPanel.add(sandboxScroll);
        centerPanel.add(Box.createVerticalStrut(10));

        JLabel portfolioLabel = new JLabel("💼 Портфель:");
        portfolioLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(portfolioLabel);
        centerPanel.add(portfolioScroll);

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

    /**
     * Обновляет список счетов
     */
    private void updateAccounts() {
        refreshButton.setEnabled(false);
        refreshButton.setText("⏳ Обновление...");

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    ConnectorConfig realConfig = new ConnectorConfig("invest.properties");
                    if (realConfig.getToken() != null && !realConfig.getToken().trim().isEmpty()) {
                        AccountsService realService = new AccountsService(
                                realConfig.getToken(),
                                realConfig.getApiUrl(),
                                realConfig.getApiPort()
                        );
                        int realCount = realService.getAccountsCount();
                        List<Account> realAccounts = realService.getAccountsList();

                        SwingUtilities.invokeLater(() -> {
                            realAccountsLabel.setText("Реальные счета: " + realCount);
                            updateAccountsTable(realAccountsTable, realAccounts);
                            populateAccountSelector(realAccounts);
                        });
                    }

                    ConnectorConfig sandboxConfig = new ConnectorConfig("sandbox.properties");
                    if (sandboxConfig.getToken() != null && !sandboxConfig.getToken().trim().isEmpty()) {
                        AccountsService sandboxService = new AccountsService(
                                sandboxConfig.getToken(),
                                sandboxConfig.getApiUrl(),
                                sandboxConfig.getApiPort()
                        );
                        int sandboxCount = sandboxService.getAccountsCount();
                        List<Account> sandboxAccounts = sandboxService.getAccountsList();

                        SwingUtilities.invokeLater(() -> {
                            sandboxAccountsLabel.setText("Sandbox счета: " + sandboxCount);
                            updateAccountsTable(sandboxAccountsTable, sandboxAccounts);
                        });
                    }
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(TinkoffInvestGui.this,
                                    "Ошибка: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE));
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

    /**
     * Заполняет dropdown со списком аккаунтов
     */
    private void populateAccountSelector(List<Account> accounts) {
        accountSelector.removeAllItems();
        accountSelector.addItem("-- Выберите счет --");

        for (Account account : accounts) {
            accountSelector.addItem(account.getId());
        }
    }

    /**
     * Показывает портфель выбранного счета
     */
    private void showPortfolio() {
        if (selectedAccountId == null || selectedAccountId.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Пожалуйста, выберите счет", "Внимание", JOptionPane.WARNING_MESSAGE);
            return;
        }

        portfolioButton.setEnabled(false);
        portfolioButton.setText("⏳ Загрузка...");

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    ConnectorConfig realConfig = new ConnectorConfig("invest.properties");
                    if (realConfig.getToken() != null && !realConfig.getToken().trim().isEmpty()) {
                        PortfolioService service = new PortfolioService(
                                realConfig.getToken(),
                                realConfig.getApiUrl(),
                                realConfig.getApiPort()
                        );
                        PortfolioResponse portfolio = service.getPortfolio(selectedAccountId);

                        SwingUtilities.invokeLater(() -> updatePortfolioTable(portfolio));
                    }
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(TinkoffInvestGui.this,
                                    "Ошибка: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }

            @Override
            protected void done() {
                portfolioButton.setEnabled(true);
                portfolioButton.setText("💼 Портфель");
            }
        };
        worker.execute();
    }

    /**
     * Обновляет таблицу счетов
     */
    private void updateAccountsTable(JTable table, List<Account> accounts) {
        if (accounts.isEmpty()) {
            table.setModel(new DefaultTableModel(new Object[][]{{"Нет счетов"}}, new String[]{"Детали"}));
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

    /**
     * Обновляет таблицу портфеля
     */
    private void updatePortfolioTable(PortfolioResponse portfolio) {
        if (PortfolioTableFormatter.isPortfolioEmpty(portfolio)) {
            portfolioTable.setModel(new DefaultTableModel(
                    new Object[][]{{"Позиций нет"}},
                    new String[]{"Информация"}));
            return;
        }

        Object[][] data = PortfolioTableFormatter.formatPortfolioData(portfolio);
        String[] headers = PortfolioTableFormatter.getPortfolioColumnHeaders();
        portfolioTable.setModel(new DefaultTableModel(data, headers));
    }

    /**
     * Форматирует тип счета
     */
    private String formatAccountType(AccountType type) {
        switch (type) {
            case ACCOUNT_TYPE_TINKOFF: return "Тинькофф брокерский";
            case ACCOUNT_TYPE_TINKOFF_IIS: return "ИИС";
            case ACCOUNT_TYPE_INVEST_BOX: return "Инвесткопилка";
            default: return type.name();
        }
    }

    /**
     * Форматирует статус счета
     */
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
                GrpcChannelManager.getInstance().shutdown();
                System.exit(0);
            }
        });
        super.addWindowListener(l);
    }

    /**
     * Останавливает автообновление портфеля при закрытии приложения
     */
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
