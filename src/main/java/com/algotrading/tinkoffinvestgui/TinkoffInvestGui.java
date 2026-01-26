package com.algotrading.tinkoffinvestgui;

import ru.tinkoff.piapi.contract.v1.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class TinkoffInvestGui extends JFrame {
    private JLabel realAccountsLabel;
    private JLabel sandboxAccountsLabel;
    private JTable realAccountsTable;
    private JTable sandboxAccountsTable;
    private JButton refreshButton;

    public TinkoffInvestGui() {
        setTitle("Tinkoff Invest Accounts");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridBagLayout());
        setSize(500, 500);
        setLocationRelativeTo(null);

        // Заголовок
        JLabel title = new JLabel("🧾 Счета Tinkoff Invest", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));

        // Лейблы количества
        realAccountsLabel = new JLabel("Реальные счета: --");
        sandboxAccountsLabel = new JLabel("Sandbox счета: --");
        realAccountsLabel.setFont(new Font("Arial", Font.BOLD, 14));
        sandboxAccountsLabel.setFont(new Font("Arial", Font.BOLD, 14));

        // Кнопка обновления
        refreshButton = new JButton("🔄 Обновить счета");
        refreshButton.addActionListener(new RefreshAction());

        // Таблицы счетов
        String[] columns = {"ID", "Название", "Тип", "Статус"};
        realAccountsTable = new JTable(new DefaultTableModel(new Object[][]{{"Загрузка..."}}, columns));
        sandboxAccountsTable = new JTable(new DefaultTableModel(new Object[][]{{"Загрузка..."}}, columns));

        JScrollPane realScroll = new JScrollPane(realAccountsTable);
        JScrollPane sandboxScroll = new JScrollPane(sandboxAccountsTable);
        realScroll.setPreferredSize(new Dimension(450, 120));
        sandboxScroll.setPreferredSize(new Dimension(450, 120));

        // Размещаем элементы
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

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        add(realScroll, gbc);

        gbc.gridy = 5;
        add(sandboxScroll, gbc);

        updateAccounts(); // Загружаем при запуске
    }

    private void updateAccounts() {
        refreshButton.setEnabled(false);
        refreshButton.setText("⏳ Обновление...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    // Реальные счета
                    ConnectorConfig realConfig = new ConnectorConfig("invest.properties");
                    if (realConfig.getToken() == null || realConfig.getToken().trim().isEmpty()) {
                        SwingUtilities.invokeLater(() ->
                                realAccountsLabel.setText("Реальные счета: ❌ Нет токена"));
                    } else {
                        AccountsService realService = new AccountsService(realConfig.getToken());
                        int realCount = realService.getAccountsCount();
                        List<Account> realAccounts = realService.getAccountsList();
                        SwingUtilities.invokeLater(() -> {
                            realAccountsLabel.setText("Реальные счета: " + realCount);
                            updateAccountsTable(realAccountsTable, realAccounts, "Реальные");
                        });
                    }

                    // Sandbox счета
                    ConnectorConfig sandboxConfig = new ConnectorConfig("sandbox.properties");
                    AccountsService sandboxService = new AccountsService(sandboxConfig.getToken());
                    int sandboxCount = sandboxService.getAccountsCount();
                    List<Account> sandboxAccounts = sandboxService.getAccountsList();
                    SwingUtilities.invokeLater(() -> {
                        sandboxAccountsLabel.setText("Sandbox счета: " + sandboxCount);
                        updateAccountsTable(sandboxAccountsTable, sandboxAccounts, "Sandbox");
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

    private void updateAccountsTable(JTable table, List<Account> accounts, String type) {
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

    private class RefreshAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            updateAccounts();
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
