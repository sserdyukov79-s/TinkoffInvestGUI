package com.algotrading.tinkoffinvestgui;

import com.algotrading.tinkoffinvestgui.api.PortfolioService;
import com.algotrading.tinkoffinvestgui.api.BondsService;
import com.algotrading.tinkoffinvestgui.api.OrdersService;
import com.algotrading.tinkoffinvestgui.api.AccountsApiService;
import com.algotrading.tinkoffinvestgui.api.CandlesApiService;
import com.algotrading.tinkoffinvestgui.config.AppConstants;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.config.DatabaseConnectionPool;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.repository.BondsRepository;
import com.algotrading.tinkoffinvestgui.repository.InstrumentsRepository;
import com.algotrading.tinkoffinvestgui.repository.ParametersRepository;
import com.algotrading.tinkoffinvestgui.service.AccountService;
import com.algotrading.tinkoffinvestgui.service.OrdersBusinessService;
import com.algotrading.tinkoffinvestgui.service.OrdersScheduler;
import com.algotrading.tinkoffinvestgui.service.CandlesExportService;
import com.algotrading.tinkoffinvestgui.service.BondsAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Главный класс GUI приложения Tinkoff Invest
 * С интегрированным планировщиком автоматических заявок
 */
public class TinkoffInvestGui extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(TinkoffInvestGui.class);

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

    // Планировщик автоматических заявок
    private OrdersScheduler ordersScheduler;

    public TinkoffInvestGui() {
        log.info("=== Запуск приложения Tinkoff Invest GUI ===");
        setTitle("Tinkoff Invest - Управление портфелем");

        // ИЗМЕНЕНО: Корректное закрытие через shutdown()
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                shutdown();
            }
        });

        setLayout(new BorderLayout());
        setSize(AppConstants.WINDOW_WIDTH, AppConstants.WINDOW_HEIGHT);
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

        log.info("Приложение успешно инициализировано");

        // НОВОЕ: Запускаем планировщик автоматических заявок
        initOrdersScheduler();
    }

    /**
     * Инициализация планировщика автоматических заявок
     * Выставляет заявки автоматически по будням после start_time из БД
     */
    private void initOrdersScheduler() {
        log.info("🔧 Инициализация планировщика автоматических заявок");

        ParametersRepository paramsRepo = new ParametersRepository();

        // Задача для выставления заявок
        Runnable ordersTask = () -> {
            try {
                log.info("📋 Запуск автоматического выставления заявок из планировщика");

                // Выполняем отправку заявок в GUI-потоке
                SwingUtilities.invokeLater(() -> {
                    try {
                        sendOrdersToExchange();
                    } catch (Exception e) {
                        log.error("❌ Ошибка при автоматическом выставлении заявок", e);
                    }
                });

            } catch (Exception e) {
                log.error("❌ Ошибка в задаче автоматического выставления заявок: {}", e.getMessage(), e);
            }
        };

        ordersScheduler = new OrdersScheduler(paramsRepo, ordersTask);
        ordersScheduler.start();

        log.info("✅ Планировщик автоматических заявок запущен");
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

        JButton viewJsonButton = new JButton("📄 Просмотр JSON");
        viewJsonButton.addActionListener(e -> showOrdersJson());
        viewJsonButton.setFont(new Font("Arial", Font.PLAIN, 12));

        JButton sendOrdersButton = new JButton("🚀 Отправить заявки на биржу");
        sendOrdersButton.addActionListener(e -> sendOrdersToExchange());
        sendOrdersButton.setFont(new Font("Arial", Font.BOLD, 12));
        sendOrdersButton.setBackground(new Color(231, 76, 60));
        sendOrdersButton.setForeground(Color.WHITE);
        sendOrdersButton.setFocusPainted(false);

        buttonsPanel.add(refreshInstrumentsButton);
        buttonsPanel.add(addInstrumentButton);
        buttonsPanel.add(editInstrumentButton);
        buttonsPanel.add(deleteInstrumentButton);
        buttonsPanel.add(Box.createHorizontalStrut(20));
        buttonsPanel.add(viewJsonButton);
        buttonsPanel.add(Box.createHorizontalStrut(10));
        buttonsPanel.add(sendOrdersButton);

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

    private void loadInstruments() {
        log.info("Запуск загрузки инструментов из БД");
        refreshInstrumentsButton.setEnabled(false);
        refreshInstrumentsButton.setText("⏳ Загрузка...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    List<Instrument> instruments = instrumentsRepository.findAll();
                    log.info("Загружено инструментов из БД: {}", instruments.size());
                    SwingUtilities.invokeLater(() -> updateInstrumentsTable(instruments));
                } catch (Exception e) {
                    log.error("Ошибка загрузки инструментов из БД", e);
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

    private void updateInstrumentsTable(List<Instrument> instruments) {
        if (instruments.isEmpty()) {
            log.warn("Список инструментов пуст");
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
        log.debug("Таблица инструментов обновлена, строк: {}", instruments.size());
    }

    private void showAddInstrumentDialog() {
        log.debug("Открытие диалога добавления инструмента");
        JDialog dialog = new JDialog(this, "Добавить инструмент", true);
        dialog.setLayout(new GridLayout(11, 2, 10, 10));
        dialog.setSize(500, 450);
        dialog.setLocationRelativeTo(this);

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

                log.info("Добавление нового инструмента: {}", instrument.getName());
                instrumentsRepository.save(instrument);
                loadInstruments();
                dialog.dispose();
                JOptionPane.showMessageDialog(this, "✓ Инструмент добавлен!",
                        "Успех", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                log.error("Ошибка добавления инструмента", ex);
                JOptionPane.showMessageDialog(dialog, "Ошибка: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> {
            log.debug("Отмена добавления инструмента");
            dialog.dispose();
        });

        dialog.add(saveButton);
        dialog.add(cancelButton);
        dialog.setVisible(true);
    }

    private void showEditInstrumentDialog() {
        int selectedRow = instrumentsTable.getSelectedRow();
        if (selectedRow == -1) {
            log.warn("Попытка редактирования без выбора инструмента");
            JOptionPane.showMessageDialog(this, "Выберите инструмент для редактирования",
                    "Внимание", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int id = (int) instrumentsTable.getValueAt(selectedRow, 0);
        log.debug("Открытие диалога редактирования инструмента ID: {}", id);

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

                log.info("Обновление инструмента ID: {}, Name: {}", id, instrument.getName());
                instrumentsRepository.update(instrument);
                loadInstruments();
                dialog.dispose();
                JOptionPane.showMessageDialog(this, "✓ Инструмент обновлён!",
                        "Успех", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                log.error("Ошибка обновления инструмента ID: {}", id, ex);
                JOptionPane.showMessageDialog(dialog, "Ошибка: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> {
            log.debug("Отмена редактирования инструмента ID: {}", id);
            dialog.dispose();
        });

        dialog.add(saveButton);
        dialog.add(cancelButton);
        dialog.setVisible(true);
    }

    private void deleteSelectedInstrument() {
        int selectedRow = instrumentsTable.getSelectedRow();
        if (selectedRow == -1) {
            log.warn("Попытка удаления без выбора инструмента");
            JOptionPane.showMessageDialog(this, "Выберите инструмент для удаления",
                    "Внимание", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int id = (int) instrumentsTable.getValueAt(selectedRow, 0);
        String name = (String) instrumentsTable.getValueAt(selectedRow, 3);

        log.debug("Запрос подтверждения удаления инструмента ID: {}, Name: {}", id, name);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Удалить инструмент \"" + name + "\"?",
                "Подтверждение удаления",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                log.info("Удаление инструмента ID: {}, Name: {}", id, name);
                instrumentsRepository.delete(id);
                loadInstruments();
                JOptionPane.showMessageDialog(this, "✓ Инструмент удалён!",
                        "Успех", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                log.error("Ошибка удаления инструмента ID: {}", id, ex);
                JOptionPane.showMessageDialog(this, "Ошибка удаления: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            log.debug("Удаление отменено пользователем");
        }
    }

    private void showOrdersJson() {
        log.info("Формирование JSON заявок");
        try {
            List<Instrument> instruments = instrumentsRepository.findAll();
            if (instruments.isEmpty()) {
                log.warn("Нет инструментов для формирования заявок");
                JOptionPane.showMessageDialog(this,
                        "Нет инструментов для формирования заявок",
                        "Внимание", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Получаем account ID из БД
            String accountId;
            try {
                accountId = AccountService.getActiveAccountId();
            } catch (Exception e) {
                log.error("❌ Не удалось получить account ID из БД", e);
                JOptionPane.showMessageDialog(this,
                        "Account ID не настроен в БД!\n\n" + e.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }

            log.debug("Формирование заявок для {} инструментов, accountId: {}",
                    instruments.size(), accountId);
            String ordersJson = OrdersService.createOrdersJson(instruments, accountId);
            log.info("JSON заявок сформирован успешно");

            JDialog dialog = new JDialog(this, "JSON заявок для отправки", false);
            dialog.setSize(800, 600);
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout(10, 10));

            JLabel titleLabel = new JLabel(
                    String.format("📤 Сформированные заявки для Account: %s", accountId),
                    SwingConstants.CENTER);
            titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
            titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            dialog.add(titleLabel, BorderLayout.NORTH);

            JTextArea jsonArea = new JTextArea(ordersJson);
            jsonArea.setEditable(false);
            jsonArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            jsonArea.setLineWrap(false);
            jsonArea.setWrapStyleWord(false);
            JScrollPane scrollPane = new JScrollPane(jsonArea);
            scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            dialog.add(scrollPane, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
            JButton copyButton = new JButton("📋 Скопировать в буфер");
            copyButton.addActionListener(e -> {
                java.awt.datatransfer.StringSelection selection =
                        new java.awt.datatransfer.StringSelection(ordersJson);
                java.awt.Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .setContents(selection, selection);
                log.info("JSON скопирован в буфер обмена");
                JOptionPane.showMessageDialog(dialog, "✓ JSON скопирован в буфер обмена!");
            });

            JButton closeButton = new JButton("❌ Закрыть");
            closeButton.addActionListener(e -> dialog.dispose());
            buttonPanel.add(copyButton);
            buttonPanel.add(closeButton);

            JPanel infoPanel = new JPanel(new BorderLayout());
            JLabel infoLabel = new JLabel(
                    "AccountID: " + accountId +
                            " | Инструментов: " + instruments.size() + "");
            infoLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            infoPanel.add(infoLabel, BorderLayout.WEST);
            infoPanel.add(buttonPanel, BorderLayout.CENTER);

            dialog.add(infoPanel, BorderLayout.SOUTH);
            dialog.setVisible(true);

        } catch (Exception e) {
            log.error("Ошибка формирования JSON заявок", e);
            JOptionPane.showMessageDialog(this,
                    "Ошибка формирования заявок: " + e.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Отправляет заявки на биржу (использует account ID из БД)
     */
    private void sendOrdersToExchange() {
        log.info("🚀 Запуск отправки заявок на биржу");
        try {
            // Получаем инструменты из БД
            List<Instrument> instruments = instrumentsRepository.findAll();
            if (instruments.isEmpty()) {
                log.warn("Нет инструментов для отправки");
                JOptionPane.showMessageDialog(this,
                        "Нет инструментов для формирования заявок",
                        "Внимание", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Проверяем, что account ID настроен в БД
            if (!AccountService.isAccountConfigured()) {
                log.error("❌ Account ID не настроен в БД");
                JOptionPane.showMessageDialog(this,
                        "Account ID не настроен в БД!\n\n" +
                                "Добавьте запись в таблицу parameters:\n" +
                                "INSERT INTO parameters (\"parameter\", value) VALUES ('account1', 'ваш_account_id');",
                        "Ошибка конфигурации", JOptionPane.ERROR_MESSAGE);
                return;
            }
// ⚠️ ПОДТВЕРЖДЕНИЕ ОТКЛЮЧЕНО ДЛЯ АВТОМАТИЧЕСКОЙ ТОРГОВЛИ
        /*
            // Подтверждение от пользователя
            String accountId = AccountService.getActiveAccountId();
            int confirm = JOptionPane.showConfirmDialog(this,
                    String.format(
                            "Отправить заявки на биржу?\n\n" +
                                    "Account ID: %s\n" +
                                    "Количество инструментов: %d\n\n" +
                                    "⚠️ Это отправит РЕАЛЬНЫЕ заявки на биржу!",
                            accountId, instruments.size()
                    ),
                    "Подтверждение отправки",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirm != JOptionPane.YES_OPTION) {
                log.info("Отправка заявок отменена пользователем");
                return;
            }
        */

            log.info("🤖 Автоматическая отправка {} заявок (без подтверждения)", instruments.size());

            // Отправляем заявки
            log.info("Начало отправки {} заявок", instruments.size());
            OrdersBusinessService service = new OrdersBusinessService();
            OrdersBusinessService.OrdersResult result = service.sendOrdersBatch(instruments);

            // Показываем результат
            if (result.hasErrors()) {
                JOptionPane.showMessageDialog(this,
                        String.format(
                                "Отправка завершена с ошибками:\n\n%s\n\n" +
                                        "Детали в логах.",
                                result.getSummary()
                        ),
                        "Результат отправки",
                        JOptionPane.WARNING_MESSAGE
                );
            } else {
                JOptionPane.showMessageDialog(this,
                        String.format(
                                "✅ Заявки успешно отправлены!\n\n%s\n\n" +
                                        "Проверьте логи для детальной информации.",
                                result.getSummary()
                        ),
                        "Успех",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }

        } catch (Exception e) {
            log.error("❌ Критическая ошибка при отправке заявок", e);
            JOptionPane.showMessageDialog(this,
                    "Ошибка отправки заявок:\n" + e.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ============================================================
    // ВКЛАДКА 2: ПОРТФЕЛЬ И СЧЕТА
    // ============================================================

    private JPanel createPortfolioPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("💼 Портфель и счета Tinkoff Invest", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));

        // Информационное сообщение
        JLabel accountsInfoLabel = new JLabel(
                "ℹ️ Информация: Счета на этой вкладке только для просмотра. " +
                        "Для отправки заявок используется Account ID из БД (parameters.account1)"
        );
        accountsInfoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        accountsInfoLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));

        accountsLabel = new JLabel("Счета: --");
        accountsLabel.setFont(new Font("Arial", Font.BOLD, 14));

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        refreshButton = new JButton("🔄 Обновить счета");
        refreshButton.addActionListener(e -> updateAccounts());

        portfolioButton = new JButton("💼 Обновить портфель");
        portfolioButton.addActionListener(e -> showPortfolio());

        buttonsPanel.add(refreshButton);
        buttonsPanel.add(portfolioButton);

        String[] accountColumns = {"ID", "Название", "Тип", "Статус"};
        accountsTable = new JTable(new DefaultTableModel(new Object[][]{{"Загрузка..."}}, accountColumns));

        String[] portfolioColumns = {"FIGI", "Тикер", "Тип", "Площадка", "Кол-во", "Средняя цена", "Стоимость"};
        portfolioTable = new JTable(new DefaultTableModel(new Object[][]{{"Загрузка..."}}, portfolioColumns));

        JScrollPane accountsScroll = new JScrollPane(accountsTable);
        JScrollPane portfolioScroll = new JScrollPane(portfolioTable);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(title);
        topPanel.add(Box.createVerticalStrut(10));
        topPanel.add(accountsInfoLabel);
        topPanel.add(Box.createVerticalStrut(5));
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
        log.info("Запуск автообновления портфеля (интервал: {} мин)", PORTFOLIO_UPDATE_INTERVAL_MINUTES);
        portfolioUpdateExecutor = Executors.newScheduledThreadPool(1);
        portfolioUpdateExecutor.scheduleAtFixedRate(
                this::showPortfolio,
                PORTFOLIO_UPDATE_INTERVAL_MINUTES,
                PORTFOLIO_UPDATE_INTERVAL_MINUTES,
                java.util.concurrent.TimeUnit.MINUTES
        );
    }

    private void updateAccountsAndPortfolio() {
        log.info("Загрузка счетов и портфеля");
        refreshButton.setEnabled(false);
        refreshButton.setText("⏳ Обновление...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    AccountsApiService service = new AccountsApiService();
                    int count = service.getAccountsCount();
                    GetAccountsResponse accounts = service.getAccounts();
                    log.info("Получено счетов из API: {}", count);

                    SwingUtilities.invokeLater(() -> {
                        accountsLabel.setText("Счета: " + count);
                        updateAccountsTable(accountsTable, accounts.getAccountsList());
                    });

                    // Загружаем портфель для первого счёта (для отображения)
                    if (!accounts.getAccountsList().isEmpty()) {
                        String accountId = accounts.getAccountsList().get(0).getId();
                        PortfolioService portfolioService = new PortfolioService(
                                ConnectorConfig.getApiToken(),
                                ConnectorConfig.API_URL,
                                ConnectorConfig.API_PORT
                        );
                        PortfolioResponse portfolio = portfolioService.getPortfolio(accountId);
                        log.info("Получено позиций в портфеле: {}", portfolio.getPositionsCount());
                        SwingUtilities.invokeLater(() -> updatePortfolioTable(portfolio));
                    }

                } catch (Exception e) {
                    log.error("Ошибка загрузки данных портфеля и счетов", e);
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
        // Используем первый счёт из таблицы для отображения портфеля
        if (accountsTable.getRowCount() == 0 || accountsTable.getValueAt(0, 0) == null) {
            log.warn("Попытка загрузки портфеля без доступных счетов");
            return;
        }

        String displayAccountId = (String) accountsTable.getValueAt(0, 0);
        log.info("Загрузка портфеля для отображения, счёта: {}", displayAccountId);

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
                    PortfolioResponse portfolio = service.getPortfolio(displayAccountId);
                    log.info("Портфель загружен, позиций: {}", portfolio.getPositionsCount());
                    SwingUtilities.invokeLater(() -> updatePortfolioTable(portfolio));
                } catch (Exception e) {
                    log.error("Ошибка загрузки портфеля для счета: {}", displayAccountId, e);
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
            log.warn("Список счетов пуст");
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
        log.debug("Таблица счетов обновлена, строк: {}", accounts.size());
    }

    private void updatePortfolioTable(PortfolioResponse portfolio) {
        if (portfolio.getPositionsCount() == 0) {
            log.warn("Портфель пуст");
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
        log.debug("Таблица портфеля обновлена, позиций: {}", portfolio.getPositionsCount());
    }

    private String formatAccountType(AccountType type) {
        switch (type) {
            case ACCOUNT_TYPE_TINKOFF:
                return "Брокерский";
            case ACCOUNT_TYPE_TINKOFF_IIS:
                return "ИИС";
            case ACCOUNT_TYPE_INVEST_BOX:
                return "Инвесткопилка";
            default:
                return type.name();
        }
    }

    private String formatAccountStatus(AccountStatus status) {
        switch (status) {
            case ACCOUNT_STATUS_OPEN:
                return "Открыт ✓";
            case ACCOUNT_STATUS_CLOSED:
                return "Закрыт ✗";
            default:
                return status.name();
        }
    }

    // ============================================================
    // ВКЛАДКА 3: ЭКСПОРТ ДАННЫХ
    // ============================================================

    private JPanel createExportPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Заголовок
        JLabel title = new JLabel("💾 Экспорт данных из Tinkoff Invest", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);

        // Центральная панель с кнопками
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        // Секция 1: Экспорт облигаций
        JPanel bondsSection = new JPanel();
        bondsSection.setLayout(new BoxLayout(bondsSection, BoxLayout.Y_AXIS));
        bondsSection.setBorder(BorderFactory.createTitledBorder("📊 Экспорт списка облигаций"));
        bondsSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JLabel bondsLabel = new JLabel(
                "Описание: Выгружает все доступные облигации из T-Bank API в таблицу БД public.exportdata"
        );
        bondsLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        bondsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        bondsButton = new JButton("📥 Выгрузить облигации в БД");
        bondsButton.setFont(new Font("Arial", Font.BOLD, 14));
        bondsButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        bondsButton.addActionListener(e -> exportBondsToDatabase());

        bondsSection.add(bondsLabel);
        bondsSection.add(Box.createVerticalStrut(10));
        bondsSection.add(bondsButton);

        // Секция 2: Анализ облигаций
        JPanel analysisSection = new JPanel();
        analysisSection.setLayout(new BoxLayout(analysisSection, BoxLayout.Y_AXIS));
        analysisSection.setBorder(BorderFactory.createTitledBorder("📈 Анализ облигаций"));
        analysisSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JLabel analysisLabel = new JLabel(
                "Описание: Анализирует облигации по заданным критериям (валюта, амортизация, dlong, риск). " +
                        "Загружает свечи за 4 месяца и рассчитывает метрики (волатильность, тренд, оценка)."
        );
        analysisLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        analysisLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton analysisButton = new JButton("🔍 Анализ облигаций");
        analysisButton.setFont(new Font("Arial", Font.BOLD, 14));
        analysisButton.setBackground(new Color(52, 152, 219));
        analysisButton.setForeground(Color.WHITE);
        analysisButton.setFocusPainted(false);
        analysisButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        analysisButton.addActionListener(e -> showBondsAnalysisDialog());

        analysisSection.add(analysisLabel);
        analysisSection.add(Box.createVerticalStrut(10));
        analysisSection.add(analysisButton);

        // Секция 3: Экспорт исторических свечей
        JPanel candlesSection = new JPanel();
        candlesSection.setLayout(new BoxLayout(candlesSection, BoxLayout.Y_AXIS));
        candlesSection.setBorder(BorderFactory.createTitledBorder("📈 Экспорт исторических свечей в CSV"));
        candlesSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JLabel candlesLabel = new JLabel(
                "Описание: Выгружает исторические свечи (OHLCV) по инструменту в CSV файл"
        );
        candlesLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        candlesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton candlesButton = new JButton("📥 Экспорт свечей в CSV");
        candlesButton.setFont(new Font("Arial", Font.BOLD, 14));
        candlesButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        candlesButton.addActionListener(e -> showCandlesExportDialog());

        candlesSection.add(candlesLabel);
        candlesSection.add(Box.createVerticalStrut(10));
        candlesSection.add(candlesButton);

        // Добавляем секции в центральную панель
        centerPanel.add(bondsSection);
        centerPanel.add(Box.createVerticalStrut(20));
        centerPanel.add(analysisSection);
        centerPanel.add(Box.createVerticalStrut(20));
        centerPanel.add(candlesSection);
        centerPanel.add(Box.createVerticalGlue());

        // Информационная панель внизу
        String downloadsPath = System.getProperty("user.home") + "\\Downloads";
        JLabel infoLabel = new JLabel(
                "ℹ️ Все данные получаются через официальный T-Bank Invest API | " +
                        "📁 CSV файлы сохраняются в папку: " + downloadsPath + "",
                SwingConstants.CENTER
        );
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(infoLabel, BorderLayout.SOUTH);
        return panel;
    }

    private void showBondsAnalysisDialog() {
        log.info("Открываем диалог анализа облигаций");
        JDialog dialog = new JDialog(this, "🔍 Анализ облигаций", true);
        dialog.setSize(450, 450);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        // Панель с параметрами фильтрации
        JPanel filtersPanel = new JPanel(new GridLayout(8, 2, 10, 10));
        filtersPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel currencyLabel = new JLabel("Валюта номинала:");
        JComboBox<String> currencyCombo = new JComboBox<>(new String[]{"RUB", "USD", "EUR", "CNY"});

        JLabel amortLabel = new JLabel("Без амортизации:");
        JCheckBox amortCheckbox = new JCheckBox();
        amortCheckbox.setSelected(true);

        JLabel minDaysLabel = new JLabel("Мин. дней до погашения:");
        JTextField minDaysField = new JTextField("3");

        JLabel maxMonthsLabel = new JLabel("Макс. месяцев до погашения:");
        JTextField maxMonthsField = new JTextField("15");

        JLabel dlongLabel = new JLabel("Требовать Dlong:");
        JCheckBox dlongCheckbox = new JCheckBox();
        dlongCheckbox.setSelected(true);

        JLabel riskLabel = new JLabel("Исключить высокий риск:");
        JCheckBox riskCheckbox = new JCheckBox();
        riskCheckbox.setSelected(true);

        filtersPanel.add(currencyLabel);
        filtersPanel.add(currencyCombo);
        filtersPanel.add(amortLabel);
        filtersPanel.add(amortCheckbox);
        filtersPanel.add(minDaysLabel);
        filtersPanel.add(minDaysField);
        filtersPanel.add(maxMonthsLabel);
        filtersPanel.add(maxMonthsField);
        filtersPanel.add(dlongLabel);
        filtersPanel.add(dlongCheckbox);
        filtersPanel.add(riskLabel);
        filtersPanel.add(riskCheckbox);

        JLabel infoLabel = new JLabel("ℹ️ Анализ займёт несколько минут для загрузки свечей");
        infoLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        filtersPanel.add(new JLabel());
        filtersPanel.add(infoLabel);

        dialog.add(filtersPanel, BorderLayout.CENTER);

        // Кнопки
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton startButton = new JButton("🚀 Начать анализ");
        JButton cancelButton = new JButton("❌ Отмена");

        startButton.addActionListener(e -> {
            try {
                BondsAnalysisService.BondsFilterCriteria criteria = new BondsAnalysisService.BondsFilterCriteria();
                criteria.setNominalCurrency((String) currencyCombo.getSelectedItem());
                criteria.setWithoutAmortization(amortCheckbox.isSelected());
                criteria.setMinDaysToMaturity(Integer.parseInt(minDaysField.getText()));
                criteria.setMaxMonthsToMaturity(Integer.parseInt(maxMonthsField.getText()));
                criteria.setRequireDlong(dlongCheckbox.isSelected());
                criteria.setExcludeHighRisk(riskCheckbox.isSelected());

                dialog.dispose();
                runBondsAnalysis(criteria);

            } catch (Exception ex) {
                log.error("Ошибка параметров анализа", ex);
                JOptionPane.showMessageDialog(dialog, "Ошибка в параметрах: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonsPanel.add(startButton);
        buttonsPanel.add(cancelButton);

        dialog.add(buttonsPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void runBondsAnalysis(BondsAnalysisService.BondsFilterCriteria criteria) {
        log.info("Запуск анализа облигаций с критериями: {}", criteria);

        // Показываем прогресс-диалог
        JDialog progressDialog = new JDialog(this, "⏳ Анализ облигаций", false);
        progressDialog.setSize(400, 150);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setLayout(new BorderLayout(10, 10));

        JLabel progressLabel = new JLabel("⏳ Загрузка облигаций...", SwingConstants.CENTER);
        progressLabel.setFont(new Font("Arial", Font.BOLD, 14));
        progressLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));

        progressDialog.add(progressLabel, BorderLayout.CENTER);
        progressDialog.add(progressBar, BorderLayout.SOUTH);
        progressDialog.setVisible(true);

        SwingWorker<List<BondsAnalysisService.BondAnalysisResult>, String> worker = new SwingWorker<>() {
            @Override
            protected List<BondsAnalysisService.BondAnalysisResult> doInBackground() {
                try {
                    publish("📡 Загрузка облигаций из API...");

                    // 1. Получаем все облигации
                    BondsService bondsService = new BondsService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT
                    );
                    BondsResponse response = bondsService.getBonds();
                    List<Bond> allBonds = response.getInstrumentsList();
                    publish(String.format("✅ Получено %d облигаций", allBonds.size()));

                    // 2. Фильтруем облигации
                    publish("🔍 Фильтрация облигаций...");
                    BondsAnalysisService analysisService = new BondsAnalysisService();
                    List<Bond> filtered = analysisService.filterBonds(allBonds, criteria);
                    publish(String.format("✅ После фильтрации: %d облигаций", filtered.size()));

                    // 3. Анализируем облигации (загружаем свечи)
                    publish("📈 Загрузка свечей и анализ (это займёт время)...");
                    CandlesApiService candlesService = new CandlesApiService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT
                    );
                    return analysisService.analyzeBonds(filtered, candlesService);

                } catch (Exception e) {
                    log.error("Ошибка анализа облигаций", e);
                    throw new RuntimeException(e);
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    log.info("Прогресс: {}", msg);
                    progressLabel.setText(msg);
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    List<BondsAnalysisService.BondAnalysisResult> results = get();
                    showAnalysisResults(results);
                } catch (Exception e) {
                    log.error("Ошибка получения результатов", e);
                    JOptionPane.showMessageDialog(TinkoffInvestGui.this,
                            "Ошибка анализа: " + e.getMessage(),
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void showAnalysisResults(List<BondsAnalysisService.BondAnalysisResult> results) {
        log.info("Показываем {} результатов анализа", results.size());

        JDialog dialog = new JDialog(this, "📊 Результаты анализа облигаций", false);
        dialog.setSize(1400, 700);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        // Таблица результатов
        String[] columns = {
                "Тикер", "Название", "FIGI", "Валюта", "Погашение",
                "Dlong", "Риск", "Волатильность", "Текущ. цена", "Ср. цена",
                "Изм. цены %", "Диапазон %", "Тренд", "⭐ Оценка"
        };

        Object[][] data = new Object[results.size()][14];
        for (int i = 0; i < results.size(); i++) {
            BondsAnalysisService.BondAnalysisResult r = results.get(i);
            data[i][0] = r.getTicker();
            data[i][1] = r.getName();
            data[i][2] = r.getFigi();
            data[i][3] = r.getNominalCurrency();
            data[i][4] = r.getMaturityDate() != null ? r.getMaturityDate().toString() : "-";
            data[i][5] = String.format("%.2f", r.getDlong());
            data[i][6] = r.getRiskLevel();
            data[i][7] = String.format("%.4f", r.getVolatility());
            data[i][8] = String.format("%.2f", r.getCurrentPrice());
            data[i][9] = String.format("%.2f", r.getAvgPrice());
            data[i][10] = String.format("%.2f%%", r.getPriceChangePercent());
            data[i][11] = String.format("%.2f%%", r.getPriceRangePercent());
            data[i][12] = String.format("%.4f", r.getTrend());
            data[i][13] = String.format("%.2f", r.getScore());
        }

        JTable table = new JTable(new DefaultTableModel(data, columns));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(table);
        dialog.add(scrollPane, BorderLayout.CENTER);

        // Информационная панель
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel(String.format(
                "📊 Найдено облигаций: %d | Отсортировано по убыванию оценки (⭐)",
                results.size()
        ));
        infoLabel.setFont(new Font("Arial", Font.BOLD, 13));
        infoPanel.add(infoLabel);
        dialog.add(infoPanel, BorderLayout.NORTH);

        // Кнопка закрытия
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton closeButton = new JButton("❌ Закрыть");
        closeButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void showCandlesExportDialog() {
        log.info("Открытие диалога экспорта исторических свечей");
        JDialog dialog = new JDialog(this, "📈 Экспорт исторических свечей", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(500, 350);
        dialog.setLocationRelativeTo(this);

        // Панель с полями ввода
        JPanel inputPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // FIGI
        JLabel figiLabel = new JLabel("FIGI инструмента:");
        JTextField figiField = new JTextField();
        figiField.setToolTipText("Например: BBG004730N88 (Сбербанк)");

        // Интервал
        JLabel intervalLabel = new JLabel("Интервал свечей:");
        String[] intervals = {"1 день", "1 неделя", "1 месяц", "1 час", "15 минут", "5 минут", "1 минута"};
        JComboBox<String> intervalCombo = new JComboBox<>(intervals);
        intervalCombo.setSelectedItem("1 день");

        // Период FROM
        JLabel fromLabel = new JLabel("Начало периода:");
        LocalDate defaultFrom = LocalDate.now().minusMonths(4).minusDays(1);
        JTextField fromField = new JTextField(defaultFrom.toString());
        fromField.setToolTipText("Формат: YYYY-MM-DD");

        // Период TO
        JLabel toLabel = new JLabel("Конец периода:");
        LocalDate defaultTo = LocalDate.now().minusDays(1);
        JTextField toField = new JTextField(defaultTo.toString());
        toField.setToolTipText("Формат: YYYY-MM-DD");

        inputPanel.add(figiLabel);
        inputPanel.add(figiField);
        inputPanel.add(intervalLabel);
        inputPanel.add(intervalCombo);
        inputPanel.add(fromLabel);
        inputPanel.add(fromField);
        inputPanel.add(toLabel);
        inputPanel.add(toField);

        // Информационная панель
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        String downloadsPath = System.getProperty("user.home") + "\\Downloads";
        JLabel info1 = new JLabel("ℹ️ Формат CSV: Date,Open,High,Low,Close,Volume");
        info1.setFont(new Font("Arial", Font.PLAIN, 11));
        JLabel info2 = new JLabel("📄 Имя файла: FIGI_YYYYMMDD-YYYYMMDD.csv");
        info2.setFont(new Font("Arial", Font.PLAIN, 11));
        JLabel info3 = new JLabel("📁 Папка: " + downloadsPath);
        info3.setFont(new Font("Arial", Font.PLAIN, 11));

        infoPanel.add(info1);
        infoPanel.add(info2);
        infoPanel.add(info3);

        // Панель кнопок
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton exportButton = new JButton("📥 Экспортировать");
        exportButton.setFont(new Font("Arial", Font.BOLD, 12));

        exportButton.addActionListener(e -> {
            try {
                String figi = figiField.getText().trim();
                if (figi.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog,
                            "FIGI не может быть пустым!",
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                LocalDate from = LocalDate.parse(fromField.getText().trim());
                LocalDate to = LocalDate.parse(toField.getText().trim());
                String intervalName = (String) intervalCombo.getSelectedItem();

                log.info("Запуск экспорта свечей: FIGI={}, период={} - {}, интервал={}",
                        figi, from, to, intervalName);

                exportButton.setEnabled(false);
                exportButton.setText("⏳ Экспорт...");

                // Запускаем экспорт в отдельном потоке
                SwingWorker<String, Void> worker = new SwingWorker<>() {
                    @Override
                    protected String doInBackground() {
                        CandlesExportService service = new CandlesExportService();
                        CandleInterval interval = CandlesExportService.getCandleInterval(intervalName);
                        return service.exportCandlesToCsv(figi, from, to, interval);
                    }

                    @Override
                    protected void done() {
                        try {
                            String filePath = get();
                            log.info("✅ Экспорт завершён успешно: {}", filePath);
                            JOptionPane.showMessageDialog(dialog,
                                    String.format("✅ Экспорт завершён успешно!\n\nФайл сохранён:\n%s", filePath),
                                    "Успех", JOptionPane.INFORMATION_MESSAGE);
                            dialog.dispose();
                        } catch (Exception ex) {
                            log.error("❌ Ошибка экспорта свечей", ex);
                            JOptionPane.showMessageDialog(dialog,
                                    "Ошибка экспорта: " + ex.getMessage(),
                                    "Ошибка", JOptionPane.ERROR_MESSAGE);
                        } finally {
                            exportButton.setEnabled(true);
                            exportButton.setText("📥 Экспортировать");
                        }
                    }
                };
                worker.execute();

            } catch (Exception ex) {
                log.error("❌ Ошибка валидации данных", ex);
                JOptionPane.showMessageDialog(dialog,
                        "Ошибка: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton cancelButton = new JButton("Отмена");
        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(exportButton);
        buttonPanel.add(cancelButton);

        // Сборка диалога
        dialog.add(inputPanel, BorderLayout.CENTER);
        dialog.add(infoPanel, BorderLayout.NORTH);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void exportBondsToDatabase() {
        log.info("Запуск экспорта облигаций в БД");
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

                    log.info("Запрос облигаций из API...");
                    BondsResponse response = bondsService.getBonds();
                    java.util.List<Bond> bonds = response.getInstrumentsList();
                    log.info("Получено облигаций из API: {}", bonds.size());

                    BondsRepository repository = new BondsRepository();
                    int exportedCount = repository.exportBonds(bonds);
                    int totalRows = repository.getRowCount();

                    log.info("Экспорт завершён. Экспортировано: {}, всего строк в БД: {}",
                            exportedCount, totalRows);

                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(TinkoffInvestGui.this,
                                "✓ Экспорт завершён успешно!\n\n" +
                                        "Таблица: public.exportdata\n" +
                                        "Экспортировано облигаций: " + exportedCount + "\n" +
                                        "Всего строк в БД (с заголовком): " + totalRows,
                                "Успех", JOptionPane.INFORMATION_MESSAGE);
                    });

                } catch (Exception e) {
                    log.error("Ошибка экспорта облигаций в БД", e);
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
                bondsButton.setText("📥 Выгрузить облигации в БД");
            }
        };
        worker.execute();
    }

    // ============================================================
    // CLEANUP И SHUTDOWN
    // ============================================================

    /**
     * Корректное закрытие приложения с освобождением ресурсов
     */
    private void shutdown() {
        log.info("🛑 Завершение работы приложения");

        // Останавливаем планировщик автоматических заявок
        if (ordersScheduler != null) {
            ordersScheduler.stop();
        }

        // Останавливаем автообновление портфеля
        stopPortfolioAutoUpdate();


        System.exit(0);
    }


    private void stopPortfolioAutoUpdate() {
        if (portfolioUpdateExecutor != null && !portfolioUpdateExecutor.isShutdown()) {
            log.info("Остановка автообновления портфеля");
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
