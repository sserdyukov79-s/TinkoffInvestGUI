package com.algotrading.tinkoffinvestgui.ui.panels;

import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.repository.InstrumentsRepository;
import com.algotrading.tinkoffinvestgui.service.AccountService;
import com.algotrading.tinkoffinvestgui.service.OrdersBusinessService;
//import com.algotrading.tinkoffinvestgui.service.OrdersService;
import com.algotrading.tinkoffinvestgui.ui.dialogs.InstrumentDialog;
import com.algotrading.tinkoffinvestgui.ui.dialogs.OrdersJsonDialog;
import com.algotrading.tinkoffinvestgui.ui.utils.AsyncTask;
import com.algotrading.tinkoffinvestgui.ui.utils.DialogUtils;
import com.algotrading.tinkoffinvestgui.ui.utils.TableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Панель управления инструментами
 */
public class InstrumentsPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(InstrumentsPanel.class);

    private final JFrame parentFrame;
    private final InstrumentsRepository instrumentsRepository;

    // UI компоненты
    private JTable instrumentsTable;
    private JButton refreshInstrumentsButton;
    private JButton addInstrumentButton;
    private JButton editInstrumentButton;
    private JButton deleteInstrumentButton;

    public InstrumentsPanel(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        this.instrumentsRepository = new InstrumentsRepository();
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Заголовок
        JLabel title = new JLabel("Управление инструментами", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        add(title, BorderLayout.NORTH);

        // Панель кнопок
        JPanel buttonsPanel = createButtonsPanel();

        // Таблица инструментов
        String[] columns = {"ID", "Дата", "FIGI", "Название", "ISIN", "Приоритет",
                "Цена покупки", "Кол-во покупки", "Цена продажи", "Кол-во продажи"};

        instrumentsTable = new JTable(new DefaultTableModel(new Object[][]{}, columns));
        instrumentsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableUtils.addCopyMenu(instrumentsTable);
        instrumentsTable.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(instrumentsTable);

        // Центральная панель
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.add(buttonsPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
    }

    private JPanel createButtonsPanel() {
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

        return buttonsPanel;
    }

    /**
     * Загрузка инструментов из БД
     */
    public void loadInstruments() {
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
                    DialogUtils.showError(parentFrame, error.getMessage());
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
        InstrumentDialog dialog = new InstrumentDialog(parentFrame, null, instrumentsRepository);
        dialog.setOnSaveCallback(this::loadInstruments);
        dialog.setVisible(true);
    }

    /**
     * Диалог редактирования инструмента
     */
    private void showEditInstrumentDialog() {
        int selectedRow = instrumentsTable.getSelectedRow();
        if (selectedRow == -1) {
            log.warn("⚠️ Не выбран инструмент для редактирования");
            JOptionPane.showMessageDialog(parentFrame, "Выберите инструмент для редактирования",
                    "Предупреждение", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int id = (int) instrumentsTable.getValueAt(selectedRow, 0);
        log.debug("✏️ Редактирование инструмента ID: {}", id);

        // Получить полные данные инструмента
        Instrument instrument = instrumentsRepository.findById(id);
        if (instrument == null) {
            DialogUtils.showError(parentFrame, "Инструмент не найден!");
            return;
        }

        InstrumentDialog dialog = new InstrumentDialog(parentFrame, instrument, instrumentsRepository);
        dialog.setOnSaveCallback(this::loadInstruments);
        dialog.setVisible(true);
    }

    /**
     * Удаление выбранного инструмента
     */
    private void deleteSelectedInstrument() {
        int selectedRow = instrumentsTable.getSelectedRow();
        if (selectedRow == -1) {
            log.warn("⚠️ Не выбран инструмент для удаления");
            DialogUtils.showWarning(parentFrame, "Выберите инструмент для удаления");
            return;
        }

        int id = (int) instrumentsTable.getValueAt(selectedRow, 0);
        String name = (String) instrumentsTable.getValueAt(selectedRow, 3);
        log.debug("🗑️ Запрос на удаление инструмента ID: {}, Name: {}", id, name);

        if (DialogUtils.confirm(parentFrame, "Подтверждение удаления",
                "Удалить инструмент \"" + name + "\"?")) {
            try {
                log.info("🗑️ Удаление инструмента ID: {}, Name: {}", id, name);
                instrumentsRepository.delete(id);
                loadInstruments();
                DialogUtils.showSuccess(parentFrame, "Инструмент успешно удалён!");
            } catch (Exception ex) {
                log.error("❌ Ошибка удаления инструмента ID: {}", id, ex);
                DialogUtils.showError(parentFrame, ex.getMessage());
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
            List<Instrument> instruments = instrumentsRepository.findAll();
            if (instruments.isEmpty()) {
                log.warn("⚠️ Нет инструментов для отображения");
                DialogUtils.showWarning(parentFrame, "Нет инструментов для генерации JSON");
                return;
            }

            // Получить account ID
            String accountId;
            try {
                accountId = AccountService.getActiveAccountId();
            } catch (Exception e) {
                log.error("❌ Ошибка получения account ID: {}", e.getMessage(), e);
                DialogUtils.showError(parentFrame, "Account ID не настроен! " + e.getMessage());
                return;
            }

            log.debug("📋 Account ID: {}, Инструментов: {}", accountId, instruments.size());
            String ordersJson = "{}"; // Заглушка
//            String ordersJson = OrdersService.createOrdersJson(instruments, accountId);
            log.info("✅ JSON сформирован");

            OrdersJsonDialog dialog = new OrdersJsonDialog(parentFrame, ordersJson, accountId, instruments.size());
            dialog.setVisible(true);

        } catch (Exception e) {
            log.error("❌ Ошибка просмотра JSON: {}", e.getMessage(), e);
            DialogUtils.showError(parentFrame, e.getMessage());
        }
    }

    /**
     * Отправка заявок на биржу
     */
    public void sendOrdersToExchange() {
        log.info("📤 Отправка заявок на биржу");
        try {
            List<Instrument> instruments = instrumentsRepository.findAll();
            if (instruments.isEmpty()) {
                log.warn("⚠️ Нет инструментов для отправки");
                DialogUtils.showWarning(parentFrame, "Нет инструментов для отправки");
                return;
            }

            // Получить account ID из БД
            if (!AccountService.isAccountConfigured()) {
                log.error("❌ Account ID не настроен в БД");
                DialogUtils.showError(parentFrame,
                        "Account ID не настроен!\n\n" +
                                "Настройте в БД таблицу parameters:\n" +
                                "INSERT INTO parameters (parameter, value) VALUES ('account1', 'your_account_id');");
                return;
            }

            String accountId = AccountService.getActiveAccountId();
            log.info("📤 Начинается пакетная отправка {} заявок", instruments.size());

            OrdersBusinessService service = new OrdersBusinessService();
            OrdersBusinessService.OrdersResult result = service.sendOrdersBatch(instruments);

            if (result.hasErrors()) {
                DialogUtils.showWarning(parentFrame,
                        String.format("%s\n\nОбновите таблицу инструментов.", result.getSummary()));
            } else {
                DialogUtils.showSuccess(parentFrame,
                        String.format("Успешно отправлены заявки!\n\n%s\n\nОбновите таблицу инструментов.",
                                result.getSummary()));
            }

        } catch (Exception e) {
            log.error("❌ Ошибка отправки заявок", e);
            DialogUtils.showError(parentFrame, e.getMessage());
        }
    }
}
