package com.algotrading.tinkoffinvestgui;

import com.algotrading.tinkoffinvestgui.api.AccountsService;
import com.algotrading.tinkoffinvestgui.api.PortfolioService;
import com.algotrading.tinkoffinvestgui.api.BondsService;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.repository.BondsRepository;
import com.algotrading.tinkoffinvestgui.repository.InstrumentsRepository;
import ru.tinkoff.piapi.contract.v1.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class TinkoffInvestGui extends JFrame {

    // Общие компоненты
    private JTabbedPane tabbedPane;
    private ScheduledExecutorService portfolioUpdateExecutor;
    private static final long PORTFOLIO_UPDATE_INTERVAL_MINUTES = 5;

    // Вкладка "Мои инструменты"
    private JTable instrumentsTable;
    private JButton refreshInstrumentsButton;
    private JButton addInstrumentButton;
    private JButton editInstrumentButton;
    private JButton deleteInstrumentButton;
    private InstrumentsRepository instrumentsRepository;

    // Вкладка "Портфель и счета"
    private JLabel accountsLabel;
    private JTable accountsTable;
    private JTable portfolioTable;
    private JButton refreshButton;
    private JButton portfolioButton;
    private JButton bondsButton;
    private String selectedAccountId = "";

    public TinkoffInvestGui() {
        setTitle("Tinkoff Invest - Управление портфелем");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setSize(1400, 850);
        setLocationRelativeTo(null);

        instrumentsRepository = new InstrumentsRepository();

        // Создаём вкладки
        tabbedPane = new JTabbedPane();

        // Вкладка 1: Мои инструменты (основная)
        JPanel instrumentsPanel = createInstrumentsPanel();
        tabbedPane.addTab("📊 Мои инструменты", instrumentsPanel);

        // Вкладка 2: Портфель и счета
        JPanel portfolioPanel = createPortfolioPanel();
        tabbedPane.addTab("💼 Портфель и счета", portfolioPanel);

        // Вкладка 3: Экспорт данных
        JPanel exportPanel = createExportPanel();
        tabbedPane.addTab("💾 Экспорт данных", exportPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // Запускаем автообновление портфеля
        startPortfolioAutoUpdate();

        // Загружаем данные при старте
        loadInstruments();
        updateAccountsAndPortfolio();
    }

    // ============================================================
    // ВКЛАДКА 1: МОИ ИНСТРУМЕНТЫ
    // ============================================================

    private JPanel createInstrumentsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Заголовок
        JLabel title = new JLabel("📊 Мои инструменты для торговли", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);

        // Панель кнопок
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        refreshInstrumentsButton = new JButton("🔄 Обновить");
        refreshInstrumentsButton.addActionListener(e -> loadInstruments());

        addInstrumentButton = new JButton("➕ Добавить");
        addInstrumentButton.addActionListener(e -> showAddInstrumentDialog());

        editInstrumentButton = new JButton("✏️ Редактировать");
        editInstrumentButton.addActionListener(e -> showEditInstrumentDialog());

        deleteInstrumentButton = new JButton("🗑️ Удалить");
        deleteInstrumentButton.addActionListener(e -> deleteSelectedInstrument());

        buttonsPanel.add(refreshInstrumentsButton);
        buttonsPanel.add(addInstrumentButton);
        buttonsPanel.add(editInstrumentButton);
        buttonsPanel.add(deleteInstrumentButton);

        // Таблица инструментов
        String[] columns = {"ID", "Дата", "FIGI", "Название", "ISIN", "Приоритет",
                "Цена покупки", "Кол-во покупки", "Цена продажи", "Кол-во продажи"};
        instrumentsTable = new JTable(new DefaultTableModel(new Object[][]{{"Загрузка..."}}, columns));
        instrumentsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        instrumentsTable.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(instrumentsTable);

        // Компоновка
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.add(buttonsPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Загружает инструменты из БД
     */
    private void loadInstruments() {
        refreshInstrumentsButton.setEnabled(false);
        refreshInstrumentsButton.setText("⏳ Загрузка...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    List<Instrument> instruments = instrumentsRepository.findAll();
                    SwingUtilities.invokeLater(() -> updateInstrumentsTable(instruments));
                } catch (Exception e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(TinkoffInvestGui.this,
                                    "Ошибка загрузки инструментов: " + e.getMessage(),
                                    "Ошибка", JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }

            @Override
            protected void done() {
                refreshInstrumentsButton.setEnabled(true);
                refreshInstrumentsButton.setText("🔄 Обновить");
            }
        };

        worker.execute();
    }

    /**
     * Обновляет таблицу инструментов
     */
    private void updateInstrumentsTable(List<Instrument> instruments) {
        if (instruments.isEmpty()) {
            instrumentsTable.setModel(new DefaultTableModel(
                    new Object[][]{{"Нет инструментов"}},
                    new String[]{"Информация"}));
            return;
        }

        Object[][] data = new Object[instruments.size()][10];
        for (int i = 0; i < instruments.size(); i++) {
            Instrument inst = instruments.get(i);
            data[i][0] = inst.getId();
            data[i][1] = inst.getBookdate();
            data[i][2] = inst.getFigi();
            data[i][3] = inst.getName();
            data[i][4] = inst.getIsin();
            data[i][5] = inst.getPriority();
            data[i][6] = inst.getBuyPrice();
            data[i][7] = inst.getBuyQuantity();
            data[i][8] = inst.getSellPrice();
            data[i][9] = inst.getSellQuantity();
        }

        instrumentsTable.setModel(new DefaultTableModel(data,
                new String[]{"ID", "Дата", "FIGI", "Название", "ISIN", "Приоритет",
                        "Цена покупки", "Кол-во покупки", "Цена продажи", "Кол-во продажи"}));

        System.out.println("✓ Загружено инструментов: " + instruments.size());
    }

    /**
     * Диалог добавления инструмента
     */
    private void showAddInstrumentDialog() {
        JDialog dialog = new JDialog(this, "Добавить инструмент", true);
        dialog.setLayout(new GridLayout(11, 2, 10, 10));
        dialog.setSize(500, 450);
        dialog.setLocationRelativeTo(this);

        // Поля ввода
        JTextField figiField = new JTextField();
        JTextField nameField = new JTextField();
        JTextField isinField = new JTextField();
        JTextField priorityField = new JTextField("1");
        JTextField buyPriceField = new JTextField();
        JTextField buyQtyField = new JTextField();
        JTextField sellPriceField = new JTextField();
        JTextField sellQtyField = new JTextField();
        JTextField bookdateField = new JTextField(LocalDate.now().toString());

        dialog.add(new JLabel("Дата (YYYY-MM-DD):"));
        dialog.add(bookdateField);
        dialog.add(new JLabel("FIGI:"));
        dialog.add(figiField);
        dialog.add(new JLabel("Название:*"));
        dialog.add(nameField);
        dialog.add(new JLabel("ISIN:*"));
        dialog.add(isinField);
        dialog.add(new JLabel("Приоритет:*"));
        dialog.add(priorityField);
        dialog.add(new JLabel("Цена покупки:"));
        dialog.add(buyPriceField);
        dialog.add(new JLabel("Кол-во покупки:"));
        dialog.add(buyQtyField);
        dialog.add(new JLabel("Цена продажи:"));
        dialog.add(sellPriceField);
        dialog.add(new JLabel("Кол-во продажи:"));
        dialog.add(sellQtyField);

        JButton saveButton = new JButton("💾 Сохранить");
        JButton cancelButton = new JButton("❌ Отмена");

        saveButton.addActionListener(e -> {
            try {
                Instrument instrument = new Instrument();
                instrument.setBookdate(LocalDate.parse(bookdateField.getText()));
                instrument.setFigi(figiField.getText().isEmpty() ? null : figiField.getText());
                instrument.setName(nameField.getText());
                instrument.setIsin(isinField.getText());
                instrument.setPriority(Integer.parseInt(priorityField.getText()));

                if (!buyPriceField.getText().isEmpty()) {
                    instrument.setBuyPrice(new BigDecimal(buyPriceField.getText()));
                }
                if (!buyQtyField.getText().isEmpty()) {
                    instrument.setBuyQuantity(Integer.parseInt(buyQtyField.getText()));
                }
                if (!sellPriceField.getText().isEmpty()) {
                    instrument.setSellPrice(new BigDecimal(sellPriceField.getText()));
                }
                if (!sellQtyField.getText().isEmpty()) {
                    instrument.setSellQuantity(Integer.parseInt(sellQtyField.getText()));
                }

                instrumentsRepository.save(instrument);
                loadInstruments();
                dialog.dispose();

                JOptionPane.showMessageDialog(this, "✓ Инструмент добавлен!",
                        "Успех", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Ошибка: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.add(saveButton);
        dialog.add(cancelButton);

        dialog.setVisible(true);
    }

    /**
     * Диалог редактирования инструмента
     */
    private void showEditInstrumentDialog() {
        int selectedRow = instrumentsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Выберите инструмент для редактирования",
                    "Внимание", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int id = (int) instrumentsTable.getValueAt(selectedRow, 0);

        // Получаем текущие данные
        LocalDate bookdate = (LocalDate) instrumentsTable.getValueAt(selectedRow, 1);
        String figi = (String) instrumentsTable.getValueAt(selectedRow, 2);
        String name = (String) instrumentsTable.getValueAt(selectedRow, 3);
        String isin = (String) instrumentsTable.getValueAt(selectedRow, 4);
        int priority = (int) instrumentsTable.getValueAt(selectedRow, 5);
        Object buyPrice = instrumentsTable.getValueAt(selectedRow, 6);
        Object buyQty = instrumentsTable.getValueAt(selectedRow, 7);
        Object sellPrice = instrumentsTable.getValueAt(selectedRow, 8);
        Object sellQty = instrumentsTable.getValueAt(selectedRow, 9);

        JDialog dialog = new JDialog(this, "Редактировать инструмент", true);
        dialog.setLayout(new GridLayout(11, 2, 10, 10));
        dialog.setSize(500, 450);
        dialog.setLocationRelativeTo(this);

        // Поля с текущими значениями
        JTextField bookdateField = new JTextField(bookdate.toString());
        JTextField figiField = new JTextField(figi != null ? figi : "");
        JTextField nameField = new JTextField(name);
        JTextField isinField = new JTextField(isin);
        JTextField priorityField = new JTextField(String.valueOf(priority));
        JTextField buyPriceField = new JTextField(buyPrice != null ? buyPrice.toString() : "");
        JTextField buyQtyField = new JTextField(buyQty != null ? buyQty.toString() : "");
        JTextField sellPriceField = new JTextField(sellPrice != null ? sellPrice.toString() : "");
        JTextField sellQtyField = new JTextField(sellQty != null ? sellQty.toString() : "");

        dialog.add(new JLabel("Дата (YYYY-MM-DD):"));
        dialog.add(bookdateField);
        dialog.add(new JLabel("FIGI:"));
        dialog.add(figiField);
        dialog.add(new JLabel("Название:*"));
        dialog.add(nameField);
        dialog.add(new JLabel("ISIN:*"));
        dialog.add(isinField);
        dialog.add(new JLabel("Приоритет:*"));
        dialog.add(priorityField);
        dialog.add(new JLabel("Цена покупки:"));
        dialog.add(buyPriceField);
        dialog.add(new JLabel("Кол-во покупки:"));
        dialog.add(buyQtyField);
        dialog.add(new JLabel("Цена продажи:"));
        dialog.add(sellPriceField);
        dialog.add(new JLabel("Кол-во продажи:"));
        dialog.add(sellQtyField);

        JButton saveButton = new JButton("💾 Сохранить");
        JButton cancelButton = new JButton("❌ Отмена");

        saveButton.addActionListener(e -> {
            try {
                Instrument instrument = new Instrument();
                instrument.setId(id);
                instrument.setBookdate(LocalDate.parse(bookdateField.getText()));
                instrument.setFigi(figiField.getText().isEmpty() ? null : figiField.getText());
                instrument.setName(nameField.getText());
                instrument.setIsin(isinField.getText());
                instrument.setPriority(Integer.parseInt(priorityField.getText()));

                if (!buyPriceField.getText().isEmpty()) {
                    instrument.setBuyPrice(new BigDecimal(buyPriceField.getText()));
                }
                if (!buyQtyField.getText().isEmpty()) {
                    instrument.setBuyQuantity(Integer.parseInt(buyQtyField.getText()));
                }
                if (!sellPriceField.getText().isEmpty()) {
                    instrument.setSellPrice(new BigDecimal(sellPriceField.getText()));
                }
                if (!sellQtyField.getText().isEmpty()) {
                    instrument.setSellQuantity(Integer.parseInt(sellQtyField.getText()));
                }

                instrumentsRepository.update(instrument);
                loadInstruments();
                dialog.dispose();

                JOptionPane.showMessageDialog(this, "✓ Инструмент обновлён!",
                        "Успех", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Ошибка: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.add(saveButton);
        dialog.add(cancelButton);

        dialog.setVisible(true);
    }

    /**
     * Удаляет выбранный инструмент
     */
    private void deleteSelectedInstrument() {
        int selectedRow = instrumentsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Выберите инструмент для удаления",
                    "Внимание", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int id = (int) instrumentsTable.getValueAt(selectedRow, 0);
        String name = (String) instrumentsTable.getValueAt(selectedRow, 3);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Удалить инструмент \"" + name + "\"?",
                "Подтверждение удаления",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                instrumentsRepository.delete(id);
                loadInstruments();
                JOptionPane.showMessageDialog(this, "✓ Инструмент удалён!",
                        "Успех", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Ошибка удаления: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ============================================================
    // ВКЛАДКА 2: ПОРТФЕЛЬ И СЧЕТА
    // ============================================================

    private JPanel createPortfolioPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Заголовок
        JLabel title = new JLabel("💼 Портфель и счета Tinkoff Invest", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));

        accountsLabel = new JLabel("Счета: --");
        accountsLabel.setFont(new Font("Arial", Font.BOLD, 14));

        // Кнопки
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

        refreshButton = new JButton("🔄 Обновить счета");
        refreshButton.addActionListener(e -> updateAccounts());

        portfolioButton = new JButton("💼 Обновить портфель");
        portfolioButton.addActionListener(e -> showPortfolio());

        buttonsPanel.add(refreshButton);
        buttonsPanel.add(portfolioButton);

        // Таблицы
        String[] accountColumns = {"ID", "Название", "Тип", "Статус"};
        accountsTable = new JTable(new DefaultTableModel(new Object[][]{{"Загрузка..."}}, accountColumns));

        String[] portfolioColumns = {"FIGI", "Тикер", "Тип", "Площадка", "Кол-во", "Средняя цена", "Стоимость"};
        portfolioTable = new JTable(new DefaultTableModel(new Object[][]{{"Загрузка..."}}, portfolioColumns));

        JScrollPane accountsScroll = new JScrollPane(accountsTable);
        JScrollPane portfolioScroll = new JScrollPane(portfolioTable);

        // Компоновка
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(title);
        topPanel.add(Box.createVerticalStrut(10));
        topPanel.add(accountsLabel);
        topPanel.add(Box.createVerticalStrut(10));
        topPanel.add(buttonsPanel);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        JLabel accountsTableLabel = new JLabel("📊 Мои счета:");
        accountsTableLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(accountsTableLabel);
        centerPanel.add(accountsScroll);
        centerPanel.add(Box.createVerticalStrut(10));

        JLabel portfolioLabel = new JLabel("💼 Портфель:");
        portfolioLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(portfolioLabel);
        centerPanel.add(portfolioScroll);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);

        return panel;
    }

    private void startPortfolioAutoUpdate() {
        portfolioUpdateExecutor = Executors.newScheduledThreadPool(1);
        portfolioUpdateExecutor.scheduleAtFixedRate(
                this::showPortfolio,
                PORTFOLIO_UPDATE_INTERVAL_MINUTES,
                PORTFOLIO_UPDATE_INTERVAL_MINUTES,
                java.util.concurrent.TimeUnit.MINUTES
        );
    }

    private void updateAccountsAndPortfolio() {
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

                        if (!accounts.getAccountsList().isEmpty()) {
                            selectedAccountId = accounts.getAccountsList().get(0).getId();
                            System.out.println("✓ Выбран счет по умолчанию: " + selectedAccountId);
                        }
                    });

                    if (!accounts.getAccountsList().isEmpty()) {
                        String accountId = accounts.getAccountsList().get(0).getId();
                        PortfolioService portfolioService = new PortfolioService(
                                ConnectorConfig.getApiToken(),
                                ConnectorConfig.API_URL,
                                ConnectorConfig.API_PORT
                        );
                        PortfolioResponse portfolio = portfolioService.getPortfolio(accountId);

                        SwingUtilities.invokeLater(() -> updatePortfolioTable(portfolio));
                    }

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(TinkoffInvestGui.this,
                                    "Ошибка загрузки данных: " + e.getMessage(),
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

    private void updateAccounts() {
        updateAccountsAndPortfolio();
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
                portfolioButton.setText("💼 Обновить портфель");
            }
        };

        worker.execute();
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

    // ============================================================
    // ВКЛАДКА 3: ЭКСПОРТ ДАННЫХ
    // ============================================================

    private JPanel createExportPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Заголовок
        JLabel title = new JLabel("💾 Экспорт данных облигаций", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);

        // Кнопка экспорта
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 50));

        bondsButton = new JButton("💾 Экспортировать облигации в БД");
        bondsButton.setFont(new Font("Arial", Font.BOLD, 16));
        bondsButton.addActionListener(e -> exportBondsToDatabase());

        centerPanel.add(bondsButton);

        JLabel infoLabel = new JLabel("<html><center>Нажмите кнопку для экспорта всех облигаций<br>" +
                "из T-Bank API в таблицу public.exportdata</center></html>", SwingConstants.CENTER);
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.add(centerPanel, BorderLayout.CENTER);
        infoPanel.add(infoLabel, BorderLayout.SOUTH);

        panel.add(infoPanel, BorderLayout.CENTER);

        return panel;
    }

    private void exportBondsToDatabase() {
        bondsButton.setEnabled(false);
        bondsButton.setText("⏳ Экспорт в БД...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    BondsService bondsService = new BondsService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT
                    );

                    System.out.println("📡 Запрашиваю облигации из API...");
                    BondsResponse response = bondsService.getBonds();
                    java.util.List<Bond> bonds = response.getInstrumentsList();
                    System.out.println("✓ Получено облигаций: " + bonds.size());

                    BondsRepository repository = new BondsRepository();
                    int exportedCount = repository.exportBonds(bonds);
                    int totalRows = repository.getRowCount();

                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(TinkoffInvestGui.this,
                                "✓ Экспорт завершён успешно!\n\n" +
                                        "Таблица: public.exportdata\n" +
                                        "Экспортировано облигаций: " + exportedCount + "\n" +
                                        "Всего строк в БД (с заголовком): " + totalRows,
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
                bondsButton.setText("💾 Экспортировать облигации в БД");
            }
        };

        worker.execute();
    }

    // ============================================================
    // CLEANUP
    // ============================================================

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
