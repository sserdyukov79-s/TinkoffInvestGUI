package com.algotrading.tinkoffinvestgui;

import ru.tinkoff.piapi.contract.v1.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class TinkoffInvestGui extends JFrame {
    private JLabel realAccountsLabel;
    private JLabel sandboxAccountsLabel;
    private JTable realAccountsTable;
    private JTable sandboxAccountsTable;
    private JTable portfolioTable;
    private JButton refreshButton;
    private JButton portfolioButton;
    private String selectedAccountId = "2079063620";

    public TinkoffInvestGui() {
        setTitle("Tinkoff Invest Accounts");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridBagLayout());
        setSize(550, 700);
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

        String[] accountColumns = {"ID", "Название", "Тип", "Статус"};
        realAccountsTable = new JTable(new DefaultTableModel(new Object[][]{{"Загрузка..."}}, accountColumns));
        sandboxAccountsTable = new JTable(new DefaultTableModel(new Object[][]{{"Загрузка..."}}, accountColumns));

        String[] portfolioColumns = {"Тикер/UID", "Кол-во", "Средняя цена", "Стоимость"};
        portfolioTable = new JTable(new DefaultTableModel(new Object[][]{{"--"}}, portfolioColumns));

        JScrollPane realScroll = new JScrollPane(realAccountsTable);
        JScrollPane sandboxScroll = new JScrollPane(sandboxAccountsTable);
        JScrollPane portfolioScroll = new JScrollPane(portfolioTable);

        realScroll.setPreferredSize(new Dimension(500, 100));
        sandboxScroll.setPreferredSize(new Dimension(500, 100));
        portfolioScroll.setPreferredSize(new Dimension(500, 150));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        add(title, gbc);

        gbc.gridy = 1; gbc.gridwidth = 1;
        add(new JLabel("Реальные:", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1;
        add(realAccountsLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        add(new JLabel("Sandbox:", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1;
        add(sandboxAccountsLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        add(refreshButton, gbc);

        gbc.gridy = 4;
        add(portfolioButton, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        add(realScroll, gbc);

        gbc.gridy = 6;
        add(sandboxScroll, gbc);

        gbc.gridy = 7; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        add(new JLabel("Портфель:", SwingConstants.CENTER), gbc);

        gbc.gridy = 8; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        add(portfolioScroll, gbc);

        updateAccounts();
    }

    private void updateAccounts() {
        refreshButton.setEnabled(false);
        refreshButton.setText("⏳ Обновление...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    ConnectorConfig realConfig = new ConnectorConfig("invest.properties");
                    if (realConfig.getToken() != null && !realConfig.getToken().trim().isEmpty()) {
                        AccountsService realService = new AccountsService(realConfig.getToken());
                        int realCount = realService.getAccountsCount();
                        List<Account> realAccounts = realService.getAccountsList();
                        SwingUtilities.invokeLater(() -> {
                            realAccountsLabel.setText("Реальные счета: " + realCount);
                            updateAccountsTable(realAccountsTable, realAccounts);
                        });
                    }

                    ConnectorConfig sandboxConfig = new ConnectorConfig("sandbox.properties");
                    AccountsService sandboxService = new AccountsService(sandboxConfig.getToken());
                    int sandboxCount = sandboxService.getAccountsCount();
                    List<Account> sandboxAccounts = sandboxService.getAccountsList();
                    SwingUtilities.invokeLater(() -> {
                        sandboxAccountsLabel.setText("Sandbox счета: " + sandboxCount);
                        updateAccountsTable(sandboxAccountsTable, sandboxAccounts);
                    });
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

    private void showPortfolio() {
        portfolioButton.setEnabled(false);
        portfolioButton.setText("⏳ Загрузка...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    ConnectorConfig realConfig = new ConnectorConfig("invest.properties");
                    if (realConfig.getToken() != null && !realConfig.getToken().trim().isEmpty()) {
                        PortfolioService service = new PortfolioService(realConfig.getToken());
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

    private void updatePortfolioTable(PortfolioResponse portfolio) {
        if (portfolio.getPositionsCount() == 0) {
            portfolioTable.setModel(new DefaultTableModel(
                    new Object[][]{{"Позиций нет"}},
                    new String[]{"Информация"}));
            return;
        }

        Object[][] data = new Object[portfolio.getPositionsCount()][4];
        for (int i = 0; i < portfolio.getPositionsCount(); i++) {
            PortfolioPosition position = portfolio.getPositions(i);

            // Получаем данные позиции
            String instrumentId = PortfolioService.getInstrumentId(position);
            String quantity = PortfolioService.formatQuantity(position.getQuantity());
            String avgPrice = PortfolioService.formatPrice(position.getAveragePositionPrice());

            // Рассчитываем стоимость = количество * средняя цена
            double qty = position.getQuantity().getUnits() + position.getQuantity().getNano() / 1e9;
            double price = position.getAveragePositionPrice().getUnits() +
                    position.getAveragePositionPrice().getNano() / 1e9;
            double totalCost = qty * price;
            String cost = String.format("%.2f ₽", totalCost);

            data[i][0] = instrumentId;
            data[i][1] = quantity;
            data[i][2] = avgPrice;
            data[i][3] = cost;
        }
        portfolioTable.setModel(new DefaultTableModel(data,
                new String[]{"Тикер/UID", "Кол-во", "Средняя цена", "Стоимость"}));
    }

    private String formatAccountType(AccountType type) {
        switch (type) {
            case ACCOUNT_TYPE_TINKOFF: return "Тинькофф брокерский";
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
