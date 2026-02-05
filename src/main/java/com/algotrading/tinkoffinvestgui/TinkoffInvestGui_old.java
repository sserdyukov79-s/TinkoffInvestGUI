package com.algotrading.tinkoffinvestgui;


import com.algotrading.tinkoffinvestgui.api.*;
import com.algotrading.tinkoffinvestgui.api.PortfolioService;
import com.algotrading.tinkoffinvestgui.config.AppConstants;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.repository.BondsRepository;
import com.algotrading.tinkoffinvestgui.repository.InstrumentsRepository;
import com.algotrading.tinkoffinvestgui.repository.ParametersRepository;
import com.algotrading.tinkoffinvestgui.service.*;
import com.algotrading.tinkoffinvestgui.ui.utils.AsyncTask;
import com.algotrading.tinkoffinvestgui.ui.utils.DialogUtils;
import com.algotrading.tinkoffinvestgui.ui.utils.TableUtils;
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
 * GUI приложение для работы с Tinkoff Invest API
 */
public class TinkoffInvestGui_old extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(TinkoffInvestGui_old.class);

    // Компоненты GUI
    private JTabbedPane tabbedPane;
    private ScheduledExecutorService portfolioUpdateExecutor;
    private static final long PORTFOLIO_UPDATE_INTERVAL_MINUTES = 5;

    // Таблица инструментов
    private JTable instrumentsTable;
    private JButton refreshInstrumentsButton;
    private JButton addInstrumentButton;
    private JButton editInstrumentButton;
    private JButton deleteInstrumentButton;
    private InstrumentsRepository instrumentsRepository;

    // Вкладка портфеля
    private JLabel accountsLabel;
    private JTable accountsTable;
    private JTable portfolioTable;
    private JButton refreshButton;
    private JButton portfolioButton;

    // Вкладка экспорта
    private JButton bondsButton;

    // Планировщик заявок
    private OrdersScheduler ordersScheduler;

    public TinkoffInvestGui_old() {
        log.info("🚀 Инициализация Tinkoff Invest GUI");

        setTitle("Tinkoff Invest - Управление инструментами и торговля");
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
        tabbedPane = new JTabbedPane();

        // 1. Вкладка "Инструменты"
        JPanel instrumentsPanel = createInstrumentsPanel();
        tabbedPane.addTab("Инструменты", instrumentsPanel);

        // 2. Вкладка "Портфель"
        JPanel portfolioPanel = createPortfolioPanel();
        tabbedPane.addTab("Портфель", portfolioPanel);

        // 3. Вкладка "Экспорт и Анализ"
        JPanel exportPanel = createExportPanel();
        tabbedPane.addTab("Экспорт и Анализ", exportPanel);

        add(tabbedPane, BorderLayout.CENTER);

        startPortfolioAutoUpdate();
        loadInstruments();
        updateAccountsAndPortfolio();

        log.info("✅ GUI инициализирован");
        initOrdersScheduler();
    }

    /**
     * Инициализация планировщика заявок с параметрами из БД (starttime)
     */
    private void initOrdersScheduler() {
        log.info("🕒 Инициализация планировщика автоматической отправки заявок");

        ParametersRepository paramsRepo = new ParametersRepository();

        Runnable ordersTask = () -> {
            try {
                log.info("📤 Планировщик: начало выполнения отправки заявок из GUI-потока");

                SwingUtilities.invokeLater(() -> {
                    try {
                        sendOrdersToExchange();
                    } catch (Exception e) {
                        log.error("❌ Ошибка отправки заявок", e);
                    }
                });
            } catch (Exception e) {
                log.error("❌ Ошибка в задаче планировщика: {}", e.getMessage(), e);
            }
        };

        ordersScheduler = new OrdersScheduler(paramsRepo, ordersTask);
        ordersScheduler.start();
        log.info("✅ Планировщик инициализирован (1 раз в день)");
    }

    // ========== ПАНЕЛЬ "ИНСТРУМЕНТЫ" ==========

    /**
     * Создание панели управления инструментами
     */
    private JPanel createInstrumentsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Управление инструментами", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        refreshInstrumentsButton = new JButton("Обновить");
        refreshInstrumentsButton.addActionListener(e -> loadInstruments());

        addInstrumentButton = new JButton("Добавить");
        addInstrumentButton.addActionListener(e -> showAddInstrumentDialog());

        editInstrumentButton = new JButton("Редактировать");
        editInstrumentButton.addActionListener(e -> showEditInstrumentDialog());

        deleteInstrumentButton = new JButton("Удалить");
        deleteInstrumentButton.addActionListener(e -> deleteSelectedInstrument());

        JButton viewJsonButton = new JButton("Просмотр JSON");
        viewJsonButton.addActionListener(e -> showOrdersJson());
        viewJsonButton.setFont(new Font("Arial", Font.PLAIN, 12));

        JButton sendOrdersButton = new JButton("Отправить заявки");
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

        String[] columns = {"ID", "Дата", "FIGI", "Название", "ISIN", "Приоритет",
                "Цена покупки", "Кол-во покупки", "Цена продажи", "Кол-во продажи"};
        instrumentsTable = new JTable(new DefaultTableModel(new Object[][]{}, columns));
        instrumentsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableUtils.addCopyMenu(instrumentsTable);
        instrumentsTable.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(instrumentsTable);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.add(buttonsPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Загрузка инструментов из БД
     */
    private void loadInstruments() {
        log.info("🔄 Загрузка инструментов из БД");
        refreshInstrumentsButton.setEnabled(false);
        refreshInstrumentsButton.setText("Загрузка...");

        AsyncTask.execute(
                () -> instrumentsRepository.findAll(),
                instruments -> {
                    log.info("✅ Загружено инструментов: {}", instruments.size());
                    updateInstrumentsTable(instruments);
                    refreshInstrumentsButton.setEnabled(true);
                    refreshInstrumentsButton.setText("Обновить");
                },
                error -> {
                    log.error("❌ Ошибка загрузки инструментов", error);
                    DialogUtils.showError(this, error.getMessage());
                    refreshInstrumentsButton.setEnabled(true);
                    refreshInstrumentsButton.setText("Обновить");
                }
        );
    }

    /**
     * Обновление таблицы инструментов
     */
    private void updateInstrumentsTable(List<Instrument> instruments) {
        if (instruments.isEmpty()) {
            log.warn("⚠️ Нет инструментов для отображения");
            instrumentsTable.setModel(new DefaultTableModel(new Object[][]{}, new String[]{}));
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

        log.debug("🔄 Таблица обновлена, строк: {}, инструментов: {}", data.length, instruments.size());
    }

    /**
     * Диалог добавления инструмента
     */
    private void showAddInstrumentDialog() {
        log.debug("➕ Открытие диалога добавления инструмента");

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
        dialog.add(new JLabel("Название:"));
        dialog.add(nameField);
        dialog.add(new JLabel("ISIN:"));
        dialog.add(isinField);
        dialog.add(new JLabel("Приоритет:"));
        dialog.add(priorityField);
        dialog.add(new JLabel("Цена покупки:"));
        dialog.add(buyPriceField);
        dialog.add(new JLabel("Кол-во покупки:"));
        dialog.add(buyQtyField);
        dialog.add(new JLabel("Цена продажи:"));
        dialog.add(sellPriceField);
        dialog.add(new JLabel("Кол-во продажи:"));
        dialog.add(sellQtyField);

        JButton saveButton = new JButton("Сохранить");
        JButton cancelButton = new JButton("Отмена");

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

                log.info("💾 Сохранение инструмента: {}", instrument.getName());
                instrumentsRepository.save(instrument);
                loadInstruments();
                dialog.dispose();
                JOptionPane.showMessageDialog(this, "Инструмент успешно добавлен!", "Успех", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                log.error("❌ Ошибка сохранения инструмента", ex);
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> {
            log.debug("❌ Отмена добавления инструмента");
            dialog.dispose();
        });

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
            log.warn("⚠️ Не выбран инструмент для редактирования");
            JOptionPane.showMessageDialog(this, "Выберите инструмент для редактирования", "Предупреждение", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int id = (int) instrumentsTable.getValueAt(selectedRow, 0);
        log.debug("✏️ Редактирование инструмента ID: {}", id);

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
        dialog.add(new JLabel("Название:"));
        dialog.add(nameField);
        dialog.add(new JLabel("ISIN:"));
        dialog.add(isinField);
        dialog.add(new JLabel("Приоритет:"));
        dialog.add(priorityField);
        dialog.add(new JLabel("Цена покупки:"));
        dialog.add(buyPriceField);
        dialog.add(new JLabel("Кол-во покупки:"));
        dialog.add(buyQtyField);
        dialog.add(new JLabel("Цена продажи:"));
        dialog.add(sellPriceField);
        dialog.add(new JLabel("Кол-во продажи:"));
        dialog.add(sellQtyField);

        JButton saveButton = new JButton("Сохранить");
        JButton cancelButton = new JButton("Отмена");

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

                log.info("💾 Обновление инструмента ID: {}, Name: {}", id, instrument.getName());
                instrumentsRepository.update(instrument);
                loadInstruments();
                dialog.dispose();
                JOptionPane.showMessageDialog(this, "Инструмент успешно обновлён!", "Успех", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                log.error("❌ Ошибка обновления инструмента ID: {}", id, ex);
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> {
            log.debug("❌ Отмена редактирования инструмента ID: {}", id);
            dialog.dispose();
        });

        dialog.add(saveButton);
        dialog.add(cancelButton);
        dialog.setVisible(true);
    }

    /**
     * Удаление выбранного инструмента
     */
    private void deleteSelectedInstrument() {
        int selectedRow = instrumentsTable.getSelectedRow();
        if (selectedRow == -1) {
            log.warn("⚠️ Не выбран инструмент для удаления");
            DialogUtils.showWarning(this, "Выберите инструмент для удаления");
            return;
        }

        int id = (int) instrumentsTable.getValueAt(selectedRow, 0);
        String name = (String) instrumentsTable.getValueAt(selectedRow, 3);

        log.debug("🗑️ Запрос на удаление инструмента ID: {}, Name: {}", id, name);

        if (DialogUtils.confirm(this, "Подтверждение удаления",
                "Удалить инструмент \"" + name + "\"?")) {
            try {
                log.info("🗑️ Удаление инструмента ID: {}, Name: {}", id, name);
                instrumentsRepository.delete(id);
                loadInstruments();
                DialogUtils.showSuccess(this, "Инструмент успешно удалён!");
            } catch (Exception ex) {
                log.error("❌ Ошибка удаления инструмента ID: {}", id, ex);
                DialogUtils.showError(this, ex.getMessage());
            }
        } else {
            log.debug("❌ Удаление отменено пользователем");
        }
    }

    /**
     * Просмотр JSON заявок
     */
    private void showOrdersJson() {
        log.info("👀 Просмотр JSON заявок");
        try {
            List instruments = instrumentsRepository.findAll();
            if (instruments.isEmpty()) {
                log.warn("⚠️ Нет инструментов для отображения");
                DialogUtils.showWarning(this, "Нет инструментов для генерации JSON");
                return;
            }

            // Получить account ID
            String accountId;
            try {
                accountId = AccountService.getActiveAccountId();
            } catch (Exception e) {
                log.error("❌ Ошибка получения account ID: {}", e.getMessage(), e);
                DialogUtils.showError(this, "Account ID не настроен! " + e.getMessage());
                return;
            }

            log.debug("📋 Account ID: {}, Инструментов: {}", accountId, instruments.size());
            String ordersJson = OrdersService.createOrdersJson(instruments, accountId);
            log.info("✅ JSON сформирован");

            JDialog dialog = new JDialog(this, "JSON Заявки", false);
            dialog.setSize(800, 600);
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout(10, 10));

            JLabel titleLabel = new JLabel(
                    String.format("Account: %s | Инструментов: %d", accountId, instruments.size()),
                    SwingConstants.CENTER);
            titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
            titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            dialog.add(titleLabel, BorderLayout.NORTH);

            JTextArea jsonArea = new JTextArea(ordersJson);
            jsonArea.setEditable(false);
            jsonArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            jsonArea.setLineWrap(false);

            JScrollPane scrollPane = new JScrollPane(jsonArea);
            scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            dialog.add(scrollPane, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

            JButton copyButton = new JButton("Копировать");
            copyButton.addActionListener(e -> {
                TableUtils.copyToClipboard(ordersJson);
                log.info("📋 JSON скопирован в буфер обмена");
                DialogUtils.showSuccess(dialog, "JSON скопирован в буфер обмена!");
            });

            JButton closeButton = new JButton("Закрыть");
            closeButton.addActionListener(e -> dialog.dispose());

            buttonPanel.add(copyButton);
            buttonPanel.add(closeButton);
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            dialog.setVisible(true);

        } catch (Exception e) {
            log.error("❌ Ошибка просмотра JSON: {}", e.getMessage(), e);
            DialogUtils.showError(this, e.getMessage());
        }
    }

    /**
     * Отправка заявок на биржу (получение account ID из БД)
     */
    private void sendOrdersToExchange() {
        log.info("📤 Отправка заявок на биржу");
        try {
            List instruments = instrumentsRepository.findAll();
            if (instruments.isEmpty()) {
                log.warn("⚠️ Нет инструментов для отправки");
                DialogUtils.showWarning(this, "Нет инструментов для отправки");
                return;
            }

            // Получить account ID из БД
            if (!AccountService.isAccountConfigured()) {
                log.error("❌ Account ID не настроен в БД");
                DialogUtils.showError(this,
                        "Account ID не настроен!\n\n" +
                                "Настройте в БД таблицу parameters:\n" +
                                "INSERT INTO parameters (parameter, value) VALUES ('account1', 'your_account_id');");
                return;
            }

            String accountId = AccountService.getActiveAccountId();

            log.info("📤 Начинается пакетная отправка {} заявок", instruments.size());
            log.info("📤 Отправка {} заявок", instruments.size());

            OrdersBusinessService service = new OrdersBusinessService();
            OrdersBusinessService.OrdersResult result = service.sendOrdersBatch(instruments);

            if (result.hasErrors()) {
                DialogUtils.showWarning(this,
                        String.format("%s\n\nОбновите таблицу инструментов.", result.getSummary()));
            } else {
                DialogUtils.showSuccess(this,
                        String.format("Успешно отправлены заявки!\n\n%s\n\nОбновите таблицу инструментов.",
                                result.getSummary()));
            }

        } catch (Exception e) {
            log.error("❌ Ошибка отправки заявок", e);
            DialogUtils.showError(this, e.getMessage());
        }
    }

    // ========== ПАНЕЛЬ "ПОРТФЕЛЬ" ==========

    /**
     * Создание панели портфеля
     */
    private JPanel createPortfolioPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Портфель Tinkoff Invest", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));

        JLabel accountsInfoLabel = new JLabel("Информация о счетах. Account ID берётся из parameters.account1");
        accountsInfoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        accountsInfoLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));

        accountsLabel = new JLabel("Счета: --");
        accountsLabel.setFont(new Font("Arial", Font.BOLD, 14));

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        refreshButton = new JButton("Обновить счета");
        refreshButton.addActionListener(e -> updateAccounts());

        portfolioButton = new JButton("Обновить портфель");
        portfolioButton.addActionListener(e -> showPortfolio());

        buttonsPanel.add(refreshButton);
        buttonsPanel.add(portfolioButton);

        String[] accountColumns = {"ID", "Название", "Тип", "Статус"};
        accountsTable = new JTable(new DefaultTableModel(new Object[][]{}, accountColumns));
        TableUtils.addCopyMenu(accountsTable);

        String[] portfolioColumns = {"FIGI", "Тикер", "Тип", "Класс", "Кол-во", "Средняя цена", "Общая стоимость"};
        portfolioTable = new JTable(new DefaultTableModel(new Object[][]{}, portfolioColumns));
        TableUtils.addCopyMenu(portfolioTable);

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

        JLabel accountsTableLabel = new JLabel("Счета:");
        accountsTableLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(accountsTableLabel);
        centerPanel.add(accountsScroll);

        centerPanel.add(Box.createVerticalStrut(10));

        JLabel portfolioLabel = new JLabel("Позиции:");
        portfolioLabel.setFont(new Font("Arial", Font.BOLD, 12));
        centerPanel.add(portfolioLabel);
        centerPanel.add(portfolioScroll);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Запуск автоматического обновления портфеля каждые 5 минут
     */
    private void startPortfolioAutoUpdate() {
        log.info("⏰ Запуск автоматического обновления портфеля каждые {} минут", PORTFOLIO_UPDATE_INTERVAL_MINUTES);
        portfolioUpdateExecutor = Executors.newScheduledThreadPool(1);
        portfolioUpdateExecutor.scheduleAtFixedRate(
                this::showPortfolio,
                PORTFOLIO_UPDATE_INTERVAL_MINUTES,
                PORTFOLIO_UPDATE_INTERVAL_MINUTES,
                java.util.concurrent.TimeUnit.MINUTES
        );
    }

    /**
     * Обновление счетов и портфеля
     */
    private void updateAccountsAndPortfolio() {
        log.info("🔄 Обновление счетов и портфеля");
        refreshButton.setEnabled(false);
        refreshButton.setText("Загрузка...");

        AsyncTask.execute(
                () -> {
                    AccountsApiService service = new AccountsApiService();
                    int count = service.getAccountsCount();
                    GetAccountsResponse accounts = service.getAccounts();
                    log.info("✅ Получено счетов из API: {}", count);

                    return new Object[] { count, accounts };
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
                    DialogUtils.showError(this, error.getMessage());
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
                    DialogUtils.showError(this, error.getMessage());
                    portfolioButton.setEnabled(true);
                    portfolioButton.setText("Обновить портфель");
                }
        );
    }

    /**
     * Обновление таблицы счетов
     */
    private void updateAccountsTable(JTable table, List<Account> accounts) {
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
            log.warn("⚠️ Нет позиций в портфеле...");
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

    // ========== ПАНЕЛЬ "ЭКСПОРТ И АНАЛИЗ" ==========

    /**
     * Создание панели экспорта и анализа
     */
    private JPanel createExportPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Экспорт данных из Tinkoff Invest", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        // 1. Секция: Экспорт облигаций
        JPanel bondsSection = new JPanel();
        bondsSection.setLayout(new BoxLayout(bondsSection, BoxLayout.Y_AXIS));
        bondsSection.setBorder(BorderFactory.createTitledBorder("Облигации"));
        bondsSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JLabel bondsLabel = new JLabel("Загрузить облигации из T-Bank API в базу данных public.exportdata");
        bondsLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        bondsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        bondsButton = new JButton("Экспортировать облигации");
        bondsButton.setFont(new Font("Arial", Font.BOLD, 14));
        bondsButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        bondsButton.addActionListener(e -> exportBondsToDatabase());

        bondsSection.add(bondsLabel);
        bondsSection.add(Box.createVerticalStrut(10));
        bondsSection.add(bondsButton);

        // 2. Секция: Анализ облигаций
        JPanel analysisSection = new JPanel();
        analysisSection.setLayout(new BoxLayout(analysisSection, BoxLayout.Y_AXIS));
        analysisSection.setBorder(BorderFactory.createTitledBorder("Анализ облигаций"));
        analysisSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JLabel analysisLabel = new JLabel(
                "Анализ волатильности и расчет цен покупки/продажи по всем облигациям с фильтрацией (валюта, dlong, " +
                        "срок погашения). Использует период анализа 4 мес из БД, волатильность для цены покупки, " +
                        "стандартная наценка для цены продажи."
        );
        analysisLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        analysisLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton analysisButton = new JButton("Анализировать облигации");
        analysisButton.setFont(new Font("Arial", Font.BOLD, 14));
        analysisButton.setBackground(new Color(52, 152, 219));
        analysisButton.setForeground(Color.WHITE);
        analysisButton.setFocusPainted(false);
        analysisButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        analysisButton.addActionListener(e -> showBondsAnalysisDialog());

        analysisSection.add(analysisLabel);
        analysisSection.add(Box.createVerticalStrut(10));
        analysisSection.add(analysisButton);

        // 2.5. Секция: Бэктестинг стратегии
        JPanel backtestSection = new JPanel();
        backtestSection.setLayout(new BoxLayout(backtestSection, BoxLayout.Y_AXIS));
        backtestSection.setBorder(BorderFactory.createTitledBorder("Бэктестинг стратегии"));
        backtestSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JLabel backtestLabel = new JLabel(
                "Историческое тестирование стратегии \"ловец дна\" на исторических данных с учётом комиссий. " +
                        "Показывает прибыльность стратегии за выбранный период."
        );
        backtestLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        backtestLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton backtestButton = new JButton("Запустить бэктестинг");
        backtestButton.setFont(new Font("Arial", Font.BOLD, 14));
        backtestButton.setBackground(new Color(155, 89, 182));
        backtestButton.setForeground(Color.WHITE);
        backtestButton.setFocusPainted(false);
        backtestButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        backtestButton.addActionListener(e -> showBacktestDialog());

        backtestSection.add(backtestLabel);
        backtestSection.add(Box.createVerticalStrut(10));
        backtestSection.add(backtestButton);

        // 3. Секция: Экспорт свечей
        JPanel candlesSection = new JPanel();
        candlesSection.setLayout(new BoxLayout(candlesSection, BoxLayout.Y_AXIS));
        candlesSection.setBorder(BorderFactory.createTitledBorder("Экспорт свечей в CSV"));
        candlesSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JLabel candlesLabel = new JLabel("Экспорт исторических OHLCV свечей в CSV файл");
        candlesLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        candlesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton candlesButton = new JButton("Экспортировать свечи в CSV");
        candlesButton.setFont(new Font("Arial", Font.BOLD, 14));
        candlesButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        candlesButton.addActionListener(e -> showCandlesExportDialog());

        candlesSection.add(candlesLabel);
        candlesSection.add(Box.createVerticalStrut(10));
        candlesSection.add(candlesButton);

        centerPanel.add(bondsSection);
        centerPanel.add(Box.createVerticalStrut(20));
        centerPanel.add(analysisSection);
        centerPanel.add(Box.createVerticalStrut(20));
        centerPanel.add(backtestSection);
        centerPanel.add(Box.createVerticalStrut(20));
        centerPanel.add(candlesSection);

        String downloadsPath = System.getProperty("user.home");
        JLabel infoLabel = new JLabel(
                "T-Bank Invest API → База данных PostgreSQL → CSV файлы в " + downloadsPath,
                SwingConstants.CENTER);
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(infoLabel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Диалог параметров анализа облигаций
     */
    private void showBondsAnalysisDialog() {
        log.info("🔍 Открытие диалога параметров анализа облигаций");

        JDialog dialog = new JDialog(this, "Параметры анализа облигаций", true);
        dialog.setSize(450, 450);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel filtersPanel = new JPanel(new GridLayout(9, 2, 10, 10));
        filtersPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel currencyLabel = new JLabel("Валюта:");
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

        JLabel volumeLabel = new JLabel("Мин. ср.дневн. объём (лотов):");
        JTextField volumeField = new JTextField("2000");
        volumeField.setToolTipText("0 = без фильтра, 2000 = отфильтровать низколиквидные");

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
        filtersPanel.add(volumeLabel);
        filtersPanel.add(volumeField);

        JLabel infoLabel = new JLabel("<html><center>Анализ использует период 4 месяца из БД</center></html>");
        infoLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);

        filtersPanel.add(new JLabel(""));
        filtersPanel.add(infoLabel);

        dialog.add(filtersPanel, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton startButton = new JButton("Запустить");
        JButton cancelButton = new JButton("Отмена");

        startButton.addActionListener(e -> {
            try {
                BondsAnalysisService.BondsFilterCriteria criteria = new BondsAnalysisService.BondsFilterCriteria();
                criteria.setNominalCurrency((String) currencyCombo.getSelectedItem());
                criteria.setWithoutAmortization(amortCheckbox.isSelected());
                criteria.setMinDaysToMaturity(Integer.parseInt(minDaysField.getText()));
                criteria.setMaxMonthsToMaturity(Integer.parseInt(maxMonthsField.getText()));
                criteria.setRequireDlong(dlongCheckbox.isSelected());
                criteria.setExcludeHighRisk(riskCheckbox.isSelected());

                double minVolume = Double.parseDouble(volumeField.getText());
                criteria.setMinAvgDailyVolume(minVolume);

                dialog.dispose();
                runBondsAnalysis(criteria);
            } catch (Exception ex) {
                log.error("❌ Ошибка параметров анализа", ex);
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonsPanel.add(startButton);
        buttonsPanel.add(cancelButton);

        dialog.add(buttonsPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    /**
     * Запуск анализа облигаций
     */
    private void runBondsAnalysis(BondsAnalysisService.BondsFilterCriteria criteria) {
        log.info("🔍 Запуск анализа облигаций, критерии: {}", criteria);

        // Диалог прогресса
        JDialog progressDialog = new JDialog(this, "Анализ облигаций...", false);
        progressDialog.setSize(400, 150);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setLayout(new BorderLayout(10, 10));

        JLabel progressLabel = new JLabel("Загрузка данных...", SwingConstants.CENTER);
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
                    publish("Загрузка облигаций из API...");

                    // 1. Загрузить все облигации
                    BondsService bondsService = new BondsService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT);

                    BondsResponse response = bondsService.getBonds();
                    List<Bond> allBonds = response.getInstrumentsList();
                    publish(String.format("Загружено %d облигаций", allBonds.size()));

                    // 2. Фильтрация
                    publish("Фильтрация по параметрам...");
                    BondsAnalysisService analysisService = new BondsAnalysisService();
                    List<Bond> filtered = analysisService.filterBonds(allBonds, criteria);
                    publish(String.format("Отфильтровано %d облигаций", filtered.size()));

                    // 3. Анализ волатильности
                    publish("Анализ волатильности и расчёт цен...");
                    CandlesApiService candlesService = new CandlesApiService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT);

                    return analysisService.analyzeBonds(filtered, candlesService, criteria);
                } catch (Exception e) {
                    log.error("❌ Ошибка анализа облигаций", e);
                    throw new RuntimeException(e);
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    log.info("📊 Прогресс: {}", msg);
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
                    log.error("❌ Ошибка получения результатов анализа", e);
                    JOptionPane.showMessageDialog(TinkoffInvestGui_old.this,
                            e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    /**
     * Отображение результатов анализа облигаций
     */
    private void showAnalysisResults(List<BondsAnalysisService.BondAnalysisResult> results) {
        log.info("📊 Отображение результатов анализа: {} облигаций", results.size());

        JDialog dialog = new JDialog(this, "Результаты анализа облигаций", false);
        dialog.setSize(1800, 800);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        String[] columns = {
                "Тикер",        // 0
                "Название",     // 1
                "FIGI",         // 2
                "Валюта",       // 3
                "Погашение",    // 4
                "Dlong",        // 5
                "Риск",         // 6
                "Волатильность,%", // 7
                "Ср.дн.объём (лот)", // 8
                "Тек. цена",    // 9
                "Средняя цена", // 10
                "Изменение, %", // 11
                "Тренд",        // 12
                "Цена покупки", // 13
                "Цена продажи", // 14
                "Скидка,%",     // 15
                "Прибыль без ком.,%", // 16
                "Чистая прибыль,%", // 17
                "Комиссии,%",   // 18
                "Комиссии,% от покупки", // 19
                "Балл"          // 20
        };

        ParametersRepository paramsRepo = new ParametersRepository();
        double brokerCommission = paramsRepo.getBrokerCommissionDecimal();
        log.info("📊 Используется комиссия брокера: {:.4f}%", brokerCommission * 100);

        Object[][] data = new Object[results.size()][columns.length];
        for (int i = 0; i < results.size(); i++) {
            BondsAnalysisService.BondAnalysisResult r = results.get(i);

            // ✅ Расчёт цен с учётом комиссии
            ParametersRepository.StrategyParameters params = paramsRepo.getStrategyParameters();
            BondStrategyCalculator.StrategyRecommendation strategy = BondStrategyCalculator.calculatePrices(r, params);

            int col = 0;
            data[i][col++] = r.getTicker();               // 0
            data[i][col++] = r.getName();                 // 1
            data[i][col++] = r.getFigi();                 // 2
            data[i][col++] = r.getNominalCurrency();      // 3
            data[i][col++] = r.getMaturityDate() != null ? r.getMaturityDate().toString() : "-"; // 4
            data[i][col++] = String.format("%.2f", r.getDlong()); // 5
            data[i][col++] = r.getRiskLevel();            // 6
            data[i][col++] = String.format("%.4f%%", (r.getVolatility() / r.getAvgPrice()) * 100); // 7
            data[i][col++] = String.format("%.0f", r.getAvgDailyVolume()); // 8
            data[i][col++] = String.format("%.2f₽", r.getCurrentPrice()); // 9
            data[i][col++] = String.format("%.2f₽", r.getAvgPrice()); // 10
            data[i][col++] = String.format("%.2f%%", r.getPriceChangePercent()); // 11
            data[i][col++] = String.format("%.4f", r.getTrend()); // 12
            data[i][col++] = strategy.getBuyPrice();      // 13
            data[i][col++] = strategy.getSellPrice();     // 14
            data[i][col++] = String.format("%.2f%%", strategy.getDiscountPercent()); // 15
            data[i][col++] = String.format("%.2f%%", strategy.getProfitWithoutCommission()); // 16
            data[i][col++] = String.format("%.2f%%", strategy.getNetProfit()); // 17
            data[i][col++] = String.format("%.2f₽", strategy.getTotalCommissions()); // 18

            double commissionPercent = (strategy.getTotalCommissions() / strategy.getBuyPrice().doubleValue()) * 100;
            data[i][col++] = String.format("%.3f%%", commissionPercent); // 19

            data[i][col++] = String.format("%.2f", r.getScore()); // 20
        }

        JTable table = new JTable(new DefaultTableModel(data, columns));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Listener для двойного клика
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow >= 0) {
                    BondsAnalysisService.BondAnalysisResult analysis = results.get(selectedRow);

                    // ✅ Пересчёт цен с учётом комиссии
                    ParametersRepository.StrategyParameters params = paramsRepo.getStrategyParameters();
                    BondStrategyCalculator.StrategyRecommendation strategy = BondStrategyCalculator.calculatePrices(analysis, params);

                    showStrategyDetails(analysis, strategy);
                }
            }
        });

        addTableCopyMenu(table);

        JScrollPane scrollPane = new JScrollPane(table);
        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel(String.format("Найдено облигаций: %d | Клик для просмотра деталей",
                results.size()));
        infoLabel.setFont(new Font("Arial", Font.BOLD, 13));
        infoPanel.add(infoLabel);
        dialog.add(infoPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton closeButton = new JButton("Закрыть");
        closeButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    /**
     * Диалог экспорта свечей в CSV
     */
    private void showCandlesExportDialog() {
        log.info("📥 Открытие диалога экспорта свечей");

        JDialog dialog = new JDialog(this, "Экспорт свечей в CSV", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(500, 350);
        dialog.setLocationRelativeTo(this);

        JPanel inputPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // FIGI
        JLabel figiLabel = new JLabel("FIGI:");
        JTextField figiField = new JTextField();
        figiField.setToolTipText("Например: BBG004730N88");

        // Интервал
        JLabel intervalLabel = new JLabel("Интервал:");
        String[] intervals = {"1 мин", "1 час", "1 день", "1 неделя", "1 месяц", "15 мин", "5 мин", "1 квартал"};
        JComboBox<String> intervalCombo = new JComboBox<>(intervals);
        intervalCombo.setSelectedItem("1 день");

        // FROM
        JLabel fromLabel = new JLabel("От (дата):");
        LocalDate defaultFrom = LocalDate.now().minusMonths(4).minusDays(1);
        JTextField fromField = new JTextField(defaultFrom.toString());
        fromField.setToolTipText("Формат: YYYY-MM-DD");

        // TO
        JLabel toLabel = new JLabel("До (дата):");
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

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        String downloadsPath = System.getProperty("user.home");
        JLabel info1 = new JLabel("CSV формат: Date,Open,High,Low,Close,Volume");
        info1.setFont(new Font("Arial", Font.PLAIN, 11));
        JLabel info2 = new JLabel("Имя файла: {FIGI}_{YYYYMMDD}-{YYYYMMDD}.csv");
        info2.setFont(new Font("Arial", Font.PLAIN, 11));
        JLabel info3 = new JLabel("Путь сохранения: " + downloadsPath);
        info3.setFont(new Font("Arial", Font.PLAIN, 11));

        infoPanel.add(info1);
        infoPanel.add(info2);
        infoPanel.add(info3);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton exportButton = new JButton("Экспортировать");
        exportButton.setFont(new Font("Arial", Font.BOLD, 12));

        exportButton.addActionListener(e -> {
            try {
                String figi = figiField.getText().trim();
                if (figi.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "Введите FIGI!", "Ошибка", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                LocalDate from = LocalDate.parse(fromField.getText().trim());
                LocalDate to = LocalDate.parse(toField.getText().trim());
                String intervalName = (String) intervalCombo.getSelectedItem();

                log.info("📥 Экспорт свечей: FIGI={}, период {} - {}, интервал={}", figi, from, to, intervalName);

                exportButton.setEnabled(false);
                exportButton.setText("Экспорт...");

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
                            log.info("✅ Свечи экспортированы: {}", filePath);
                            JOptionPane.showMessageDialog(dialog,
                                    String.format("Свечи успешно экспортированы!\n%s", filePath),
                                    "Успех", JOptionPane.INFORMATION_MESSAGE);
                            dialog.dispose();
                        } catch (Exception ex) {
                            log.error("❌ Ошибка экспорта свечей", ex);
                            JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                        } finally {
                            exportButton.setEnabled(true);
                            exportButton.setText("Экспортировать");
                        }
                    }
                };

                worker.execute();
            } catch (Exception ex) {
                log.error("❌ Ошибка параметров экспорта", ex);
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton cancelButton = new JButton("Отмена");
        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(exportButton);
        buttonPanel.add(cancelButton);

        dialog.add(inputPanel, BorderLayout.CENTER);
        dialog.add(infoPanel, BorderLayout.NORTH);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    /**
     * Экспорт облигаций в БД
     */
    private void exportBondsToDatabase() {
        log.info("📤 Экспорт облигаций в базу данных");
        bondsButton.setEnabled(false);
        bondsButton.setText("Экспорт...");

        AsyncTask.execute(
                () -> {
                    BondsService bondsService = new BondsService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT
                    );

                    log.info("📡 Загрузка облигаций из API...");
                    BondsResponse response = bondsService.getBonds();
                    List bonds = response.getInstrumentsList();
                    log.info("📊 Получено облигаций из API: {}", bonds.size());

                    BondsRepository repository = new BondsRepository();
                    int exportedCount = repository.exportBonds(bonds);
                    int totalRows = repository.getRowCount();

                    log.info("✅ Экспорт завершён. Обновлено строк: {}, всего строк: {}",
                            exportedCount, totalRows);

                    return new Object[] { exportedCount, totalRows };
                },
                result -> {
                    int exportedCount = (int) ((Object[]) result)[0];
                    int totalRows = (int) ((Object[]) result)[1];

                    DialogUtils.showSuccess(this,
                            "Облигации успешно экспортированы!\n\n" +
                                    "Таблица: public.exportdata\n" +
                                    "Обновлено строк: " + exportedCount + "\n" +
                                    "Всего строк: " + totalRows);

                    bondsButton.setEnabled(true);
                    bondsButton.setText("Экспортировать облигации");
                },
                error -> {
                    log.error("❌ Ошибка экспорта облигаций", error);
                    DialogUtils.showError(this, error.getMessage());
                    bondsButton.setEnabled(true);
                    bondsButton.setText("Экспортировать облигации");
                }
        );
    }

    /**
     * Отображение детальной информации о стратегии
     */
    private void showStrategyDetails(
            BondsAnalysisService.BondAnalysisResult analysis,
            BondStrategyCalculator.StrategyRecommendation strategy) {

        JDialog detailsDialog = new JDialog(this, "Детали стратегии", true);
        detailsDialog.setSize(600, 550);
        detailsDialog.setLocationRelativeTo(this);
        detailsDialog.setLayout(new BorderLayout(15, 15));

        JLabel titleLabel = new JLabel(
                String.format("%s (%s)", analysis.getTicker(), analysis.getName()),
                SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        detailsDialog.add(titleLabel, BorderLayout.NORTH);

        JPanel paramsPanel = new JPanel(new GridLayout(14, 2, 10, 10));
        paramsPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        addParamRow(paramsPanel, "Текущая цена:", String.format("%.2f₽", analysis.getCurrentPrice()));
        addParamRow(paramsPanel, "Волатильность:", String.format("%.4f (%.2f%%)", analysis.getVolatility(), strategy.getVolatilityPercent()));
        addParamRow(paramsPanel, "Тренд:", String.format("%.4f (%.2f%%)", analysis.getTrend(), analysis.getTrend() * 100));

        addParamRow(paramsPanel, "--- ПОКУПКА ---", "");
        addParamRow(paramsPanel, "Цена покупки:", String.format("%.2f₽", strategy.getBuyPrice().doubleValue()));
        addParamRow(paramsPanel, "Скидка:", String.format("%.2f%%", strategy.getDiscountPercent()));

        addParamRow(paramsPanel, "--- ПРОДАЖА ---", "");
        addParamRow(paramsPanel, "Цена продажи:", String.format("%.2f₽", strategy.getSellPrice().doubleValue()));

        addParamRow(paramsPanel, "--- КОМИССИИ (0.04%) ---", "");
        addParamRow(paramsPanel, "Комиссия покупки (0.04%):", String.format("%.2f₽", strategy.getBuyCommission()));
        addParamRow(paramsPanel, "Комиссия продажи (0.04%):", String.format("%.2f₽", strategy.getSellCommission()));
        addParamRow(paramsPanel, "Всего комиссий:", String.format("%.2f₽", strategy.getTotalCommissions()));

        addParamRow(paramsPanel, "--- ПРИБЫЛЬ ---", "");
        addParamRow(paramsPanel, "Чистая прибыль:", String.format("%.2f₽ (%.2f%%)", strategy.getNetProfit(), strategy.getProfitPercent()));

        addParamRow(paramsPanel, "Dlong:", String.format("%.2f", analysis.getDlong()));
        addParamRow(paramsPanel, "Балл:", String.format("%.0f", analysis.getScore()));

        JScrollPane scrollPane = new JScrollPane(paramsPanel);
        detailsDialog.add(scrollPane, BorderLayout.CENTER);

        JTextArea recommendationArea = new JTextArea();
        recommendationArea.setText(strategy.getRecommendation());
        recommendationArea.setEditable(false);
        recommendationArea.setLineWrap(true);
        recommendationArea.setWrapStyleWord(true);
        recommendationArea.setFont(new Font("Arial", Font.PLAIN, 12));
        recommendationArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        recommendationArea.setBackground(new Color(240, 248, 255));

        JScrollPane recScrollPane = new JScrollPane(recommendationArea);
        recScrollPane.setBorder(BorderFactory.createTitledBorder("Рекомендация"));
        recScrollPane.setPreferredSize(new Dimension(560, 150));
        detailsDialog.add(recScrollPane, BorderLayout.SOUTH);

        detailsDialog.setVisible(true);
    }

    /**
     * Добавление строки параметра
     */
    private void addParamRow(JPanel panel, String label, String value) {
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(new Font("Arial", Font.BOLD, 12));

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(new Font("Arial", Font.PLAIN, 12));

        if (value.isEmpty() || value.startsWith("---")) {
            valueComp.setForeground(Color.GRAY);
        }

        panel.add(labelComp);
        panel.add(valueComp);
    }

    // ========== БЭКТЕСТИНГ ==========

    /**
     * Диалог параметров бэктестинга
     */
    private void showBacktestDialog() {
        log.info("🧪 Открытие диалога параметров бэктестинга");

        JDialog dialog = new JDialog(this, "Параметры бэктестинга", true);
        dialog.setSize(500, 550); // Увеличил высоту для 9 параметров
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(15, 15));

        JPanel paramsPanel = new JPanel(new GridLayout(9, 2, 10, 10)); // 9 рядов
        paramsPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        JLabel startDateLabel = new JLabel("Дата начала:");
        JTextField startDateField = new JTextField(LocalDate.now().minusYears(1).toString());

        JLabel endDateLabel = new JLabel("Дата окончания:");
        JTextField endDateField = new JTextField(LocalDate.now().toString());

        JLabel currencyLabel = new JLabel("Валюта:");
        JComboBox<String> currencyCombo = new JComboBox<>(new String[]{"RUB", "USD", "EUR", "CNY"});
        currencyCombo.setSelectedItem("RUB");

        JLabel amortLabel = new JLabel("Без амортизации:");
        JCheckBox amortCheckbox = new JCheckBox();
        amortCheckbox.setSelected(true);

        JLabel minDaysLabel = new JLabel("Мин. дней до погашения:");
        JTextField minDaysField = new JTextField("3");

        JLabel maxMonthsLabel = new JLabel("Макс. месяцев до погашения:");
        JTextField maxMonthsField = new JTextField("15");

        JLabel dlongLabel = new JLabel("Dlong > 0:");
        JCheckBox dlongCheckbox = new JCheckBox();
        dlongCheckbox.setSelected(true);

        JLabel riskLabel = new JLabel("Исключить высокий риск:");
        JCheckBox riskCheckbox = new JCheckBox();
        riskCheckbox.setSelected(true);

        JLabel volumeLabel = new JLabel("Мин. ср.дневн. объём (лот):");
        JTextField volumeField = new JTextField("2000");
        volumeField.setToolTipText("0 = без фильтра, 2000 = отфильтровать низколиквидные");

        paramsPanel.add(startDateLabel);
        paramsPanel.add(startDateField);
        paramsPanel.add(endDateLabel);
        paramsPanel.add(endDateField);
        paramsPanel.add(currencyLabel);
        paramsPanel.add(currencyCombo);
        paramsPanel.add(amortLabel);
        paramsPanel.add(amortCheckbox);
        paramsPanel.add(minDaysLabel);
        paramsPanel.add(minDaysField);
        paramsPanel.add(maxMonthsLabel);
        paramsPanel.add(maxMonthsField);
        paramsPanel.add(dlongLabel);
        paramsPanel.add(dlongCheckbox);
        paramsPanel.add(riskLabel);
        paramsPanel.add(riskCheckbox);
        paramsPanel.add(volumeLabel);
        paramsPanel.add(volumeField);

        dialog.add(paramsPanel, BorderLayout.CENTER);

        JTextArea descArea = new JTextArea(
                "Бэктестинг стратегии \"ловец дна\" на исторических данных.\n\n" +
                        "✅ Используются те же параметры что и в анализе\n" +
                        "✅ Учитывается комиссия брокера из БД (0.04%)\n" +
                        "✅ Выход: таргет достигнут или 30 дней прошло"
        );
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setFont(new Font("Arial", Font.PLAIN, 11));
        descArea.setBackground(new Color(240, 248, 255));
        descArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dialog.add(descArea, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton runButton = new JButton("Запустить");
        runButton.setFont(new Font("Arial", Font.BOLD, 12));

        runButton.addActionListener(e -> {
            try {
                LocalDate startDate = LocalDate.parse(startDateField.getText());
                LocalDate endDate = LocalDate.parse(endDateField.getText());

                BondStrategyBacktestService.BacktestFilters filters = new BondStrategyBacktestService.BacktestFilters();
                filters.currency = (String) currencyCombo.getSelectedItem();
                filters.withoutAmortization = amortCheckbox.isSelected();
                filters.minDaysToMaturity = Integer.parseInt(minDaysField.getText());
                filters.maxMonthsToMaturity = Integer.parseInt(maxMonthsField.getText());
                filters.requireDlong = dlongCheckbox.isSelected();
                filters.excludeHighRisk = riskCheckbox.isSelected();
                filters.minAvgDailyVolume = Double.parseDouble(volumeField.getText());

                dialog.dispose();
                runBacktest(startDate, endDate, filters);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton cancelButton = new JButton("Отмена");
        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(runButton);
        buttonPanel.add(cancelButton);

        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    /**
     * Запуск бэктестинга // FILTERS с минимальным объёмом
     */
    private void runBacktest(LocalDate startDate, LocalDate endDate, BondStrategyBacktestService.BacktestFilters filters) {
        log.info("🧪 Запуск бэктестинга: {} - {}, валюта={}, без_амортизации={}",
                startDate, endDate, filters.currency, filters.withoutAmortization);

        JDialog progressDialog = new JDialog(this, "Бэктестинг...", false);
        progressDialog.setSize(400, 150);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setLayout(new BorderLayout(10, 10));

        JLabel progressLabel = new JLabel("Загрузка данных...", SwingConstants.CENTER);
        progressLabel.setFont(new Font("Arial", Font.BOLD, 14));
        progressDialog.add(progressLabel, BorderLayout.CENTER);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressDialog.add(progressBar, BorderLayout.SOUTH);

        progressDialog.setVisible(true);

        SwingWorker<BondStrategyBacktestService.BacktestReport, Void> worker = new SwingWorker<>() {
            @Override
            protected BondStrategyBacktestService.BacktestReport doInBackground() {
                try {
                    CandlesApiService candlesApi = new CandlesApiService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT
                    );

                    BondsRepository bondsRepo = new BondsRepository();
                    ParametersRepository paramsRepo = new ParametersRepository();

                    BondStrategyBacktestService backtestService = new BondStrategyBacktestService(candlesApi, bondsRepo, paramsRepo);

                    return backtestService.runBacktest(startDate, endDate, filters);
                } catch (Exception e) {
                    log.error("❌ Ошибка бэктестинга", e);
                    throw new RuntimeException(e);
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    BondStrategyBacktestService.BacktestReport report = get();
                    showBacktestReport(report);
                } catch (Exception e) {
                    log.error("❌ Ошибка получения результатов бэктестинга", e);
                    JOptionPane.showMessageDialog(TinkoffInvestGui_old.this,
                            e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    /**
     * ✅ ИСПРАВЛЕННОЕ: Отображение отчёта бэктестинга с Dlong и прибылью без комиссии
     */
    private void showBacktestReport(BondStrategyBacktestService.BacktestReport report) {
        log.info("📊 Отображение отчёта бэктестинга: {} облигаций, {} сделок",
                report.getTotalBonds(), report.getTotalTrades());

        JDialog dialog = new JDialog(this, "Результаты бэктестинга", false);
        dialog.setSize(1400, 800);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel statsPanel = new JPanel(new GridLayout(2, 5, 15, 10));
        statsPanel.setBorder(BorderFactory.createTitledBorder("Общая статистика"));
        statsPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        addStatLabel(statsPanel, "Период:",
                String.format("%s — %s", report.getStartDate(), report.getEndDate()));
        addStatLabel(statsPanel, "Облигаций:", String.valueOf(report.getTotalBonds()));
        addStatLabel(statsPanel, "Сделок:", String.valueOf(report.getTotalTrades()));
        addStatLabel(statsPanel, "Прибыльных:",
                String.format("%d (%.1f%%)", report.getProfitableTrades(), report.getWinRate()));
        addStatLabel(statsPanel, "Убыточных:", String.valueOf(report.getLosingTrades()));
        addStatLabel(statsPanel, "Общая прибыль:",
                String.format("%.2f₽", report.getTotalProfit()));
        addStatLabel(statsPanel, "Средняя прибыль:",
                String.format("%.2f₽ (%.2f%%)", report.getAvgProfitPerTrade(), report.getAvgProfitPercent()));
        addStatLabel(statsPanel, "Ср. удержание (дн.):",
                String.format("%.1f дн.", report.getAvgHoldingDays()));
        addStatLabel(statsPanel, "Винрейт:",
                String.format("%.1f%%", report.getWinRate()));
        addStatLabel(statsPanel, "Период анализа:",
                String.format("%d мес.", report.getAnalysisPeriodMonths()));

        dialog.add(statsPanel, BorderLayout.NORTH);

        // ✅ ОБНОВЛЕННЫЕ КОЛОНКИ: добавлены прибыль БЕЗ комиссии
        String[] columns = {
                "Тикер",
                "Название",
                "FIGI",
                "Dlong",
                "Ср.дн.объём",
                "Сделок",
                "Прибыльных",
                "Убыточных",
                "Винрейт,%",
                "Общ.приб. БЕЗ ком.,₽",
                "Общ.чист.приб.,₽",           // 9
                "Ср.приб. БЕЗ ком.,₽",
                "Ср.чист.приб.,₽",            // 11
                "Ср.чист.приб.,%",            // 12
                "Ср.удержание,дн"             // 13
        };

        List<BondStrategyBacktestService.BondBacktestResult> results = report.getBondResults();
        Object[][] data = new Object[results.size()][columns.length];

        for (int i = 0; i < results.size(); i++) {
            BondStrategyBacktestService.BondBacktestResult r = results.get(i);

            data[i][0] = r.getTicker();
            data[i][1] = r.getName();
            data[i][2] = r.getFigi();
            data[i][3] = String.format("%.2f", r.getDlong()); // ✅ НОВОЕ: Dlong
            data[i][4] = String.format("%.0f", r.getAvgDailyVolume()); // ✅ НОВОЕ: Объём
            data[i][5] = r.getTotalTrades();
            data[i][6] = r.getProfitableTrades();
            data[i][7] = r.getLosingTrades();
            data[i][8] = String.format("%.1f%%", r.getWinRate());
            data[i][9] = String.format("%.2f₽", r.getTotalProfitBeforeCommission()); // ✅ НОВОЕ
            data[i][10] = String.format("%.2f₽", r.getTotalProfit());
            data[i][11] = String.format("%.2f₽", r.getAvgProfitBeforeCommission()); // ✅ НОВОЕ
            data[i][12] = String.format("%.2f₽", r.getAvgProfit());
            data[i][13] = String.format("%.2f%%", r.getAvgProfitPercent());
            data[i][14] = String.format("%.1f", r.getAvgHoldingDays());
        }

        JTable table = new JTable(new DefaultTableModel(data, columns));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow >= 0) {
                    BondStrategyBacktestService.BondBacktestResult bondResult = results.get(selectedRow);
                    showBondTradesDialog(bondResult);
                }
            }
        });

        addTableCopyMenu(table);

        JScrollPane scrollPane = new JScrollPane(table);
        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("Клик на строку для просмотра сделок по облигации");
        infoLabel.setFont(new Font("Arial", Font.BOLD, 12));
        infoPanel.add(infoLabel);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(infoPanel, BorderLayout.NORTH);

        JButton closeButton = new JButton("Закрыть");
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(closeButton);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(bottomPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    /**
     * Добавление строки статистики
     */
    private void addStatLabel(JPanel panel, String label, String value) {
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(new Font("Arial", Font.BOLD, 11));

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(new Font("Arial", Font.PLAIN, 11));

        panel.add(labelComp);
        panel.add(valueComp);
    }

    /**
     * Отображение детальной информации о сделках по облигации
     */
    private void showBondTradesDialog(BondStrategyBacktestService.BondBacktestResult bondResult) {
        JDialog dialog = new JDialog(this,
                String.format("Сделки: %s (%s)", bondResult.getTicker(), bondResult.getName()),
                true);
        dialog.setSize(1200, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        String[] columns = {
                "Дата покупки",
                "Цена покупки",
                "Волатильность",
                "Дата продажи",
                "Цена продажи",
                "Удержание,дн",
                "Прибыль,₽",
                "Прибыль,%"
        };

        List<BondStrategyBacktestService.Trade> trades = bondResult.getTrades();
        Object[][] data = new Object[trades.size()][columns.length];

        for (int i = 0; i < trades.size(); i++) {
            BondStrategyBacktestService.Trade t = trades.get(i);

            data[i][0] = t.getBuyDate();
            data[i][1] = String.format("%.2f₽", t.getBuyPrice());
            data[i][2] = String.format("%.4f", t.getVolatility());
            data[i][3] = t.getSellDate();
            data[i][4] = String.format("%.2f₽", t.getSellPrice());
            data[i][5] = t.getHoldingDays();
            data[i][6] = String.format("%.2f₽", t.getProfit());
            data[i][7] = String.format("%.2f%%", t.getProfitPercent());
        }

        JTable table = new JTable(new DefaultTableModel(data, columns));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        addTableCopyMenu(table);

        JScrollPane scrollPane = new JScrollPane(table);
        dialog.add(scrollPane, BorderLayout.CENTER);

        JButton closeButton = new JButton("Закрыть");
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(closeButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    // ========== УТИЛИТЫ: Копирование таблиц в Excel (TAB) ==========

    /**
     * Добавление контекстного меню для копирования таблицы в Excel
     */
    private void addTableCopyMenu(JTable table) {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem copyAllItem = new JMenuItem("Копировать всё (Excel формат)");
        copyAllItem.setFont(new Font("Arial", Font.BOLD, 12));

        copyAllItem.addActionListener(e -> {
            try {
                String data = getTableDataWithHeaders(table);
                copyToClipboard(data);
                JOptionPane.showMessageDialog(this,
                        String.format("Скопировано %d строк + заголовки.\n\nВставьте в Excel (Ctrl+V)",
                                table.getRowCount()),
                        "Копирование", JOptionPane.INFORMATION_MESSAGE);
                log.info("📋 Таблица скопирована в буфер обмена: {} строк", table.getRowCount());
            } catch (Exception ex) {
                log.error("❌ Ошибка копирования таблицы", ex);
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        popupMenu.add(copyAllItem);
        table.setComponentPopupMenu(popupMenu);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    /**
     * Получение данных таблицы с заголовками (Excel формат: TAB разделитель)
     */
    private String getTableDataWithHeaders(JTable table) {
        StringBuilder sb = new StringBuilder();

        // 1. Заголовки
        int columnCount = table.getColumnCount();
        for (int col = 0; col < columnCount; col++) {
            sb.append(table.getColumnName(col));
            if (col < columnCount - 1) {
                sb.append("\t");
            }
        }
        sb.append("\n");

        // 2. Данные
        int rowCount = table.getRowCount();
        for (int row = 0; row < rowCount; row++) {
            for (int col = 0; col < columnCount; col++) {
                Object value = table.getValueAt(row, col);
                sb.append(value != null ? value.toString() : "");
                if (col < columnCount - 1) {
                    sb.append("\t");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Копирование текста в буфер обмена
     */
    private void copyToClipboard(String text) {
        java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(selection, selection);
    }

    // ========== CLEANUP + SHUTDOWN ==========

    /**
     * Остановка планировщика и пула обновления портфеля при закрытии приложения
     */
    private void shutdown() {
        log.info("🛑 Остановка приложения");

        if (ordersScheduler != null) {
            ordersScheduler.stop();
        }

        stopPortfolioAutoUpdate();
        System.exit(0);
    }

    /**
     * Остановка автоматического обновления портфеля
     */
    private void stopPortfolioAutoUpdate() {
        if (portfolioUpdateExecutor != null && !portfolioUpdateExecutor.isShutdown()) {
            log.info("⏹️ Остановка автоматического обновления портфеля");
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

    /**
     * Точка входа в приложение
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            new TinkoffInvestGui_old().setVisible(true);
        });
    }
}
