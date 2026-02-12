package com.algotrading.tinkoffinvestgui.ui.dialogs;

import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.repository.InstrumentsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Диалог добавления/редактирования инструмента
 */
public class InstrumentDialog extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(InstrumentDialog.class);

    private final Instrument existingInstrument; // null для добавления
    private final InstrumentsRepository repository;

    // UI компоненты
//    private JTextField bookdateField;
    private JTextField figiField;
    private JTextField nameField;
    private JTextField isinField;
    private JTextField priorityField;
    private JTextField buyQtyField;
    private JTextField buyPriceField;
    private JTextField buyManualPriceField;
    private JTextField sellQtyField;
    private JTextField sellPriceField;
    private JTextField sellManualPriceField;

    private Runnable onSaveCallback;

    public InstrumentDialog(JFrame parent, Instrument existingInstrument, InstrumentsRepository repository) {
        super(parent, existingInstrument == null ? "Добавить инструмент" : "Редактировать инструмент", true);
        this.existingInstrument = existingInstrument;
        this.repository = repository;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new GridLayout(12, 2, 10, 10));
        setSize(500, 450);
        setLocationRelativeTo(getParent());

        // Инициализация полей
//        bookdateField = new JTextField(existingInstrument != null ?
//                existingInstrument.getBookdate().toString() : LocalDate.now().toString());
        figiField = new JTextField(existingInstrument != null && existingInstrument.getFigi() != null ?
                existingInstrument.getFigi() : "");
        nameField = new JTextField(existingInstrument != null ? existingInstrument.getName() : "");
        isinField = new JTextField(existingInstrument != null ? existingInstrument.getIsin() : "");
        priorityField = new JTextField(existingInstrument != null ?
                String.valueOf(existingInstrument.getPriority()) : "1");
        buyQtyField = new JTextField(existingInstrument != null && existingInstrument.getBuyQuantity() != null ?
                String.valueOf(existingInstrument.getBuyQuantity()) : "");
        buyPriceField = new JTextField(existingInstrument != null && existingInstrument.getBuyPrice() != null ?
                existingInstrument.getBuyPrice().toString() : "");
        buyManualPriceField = new JTextField(existingInstrument != null && existingInstrument.getManualBuyPrice() != null ?
                existingInstrument.getManualBuyPrice().toString() : "");
        sellQtyField = new JTextField(existingInstrument != null && existingInstrument.getSellQuantity() != null ?
                String.valueOf(existingInstrument.getSellQuantity()) : "");
        sellPriceField = new JTextField(existingInstrument != null && existingInstrument.getSellPrice() != null ?
                existingInstrument.getSellPrice().toString() : "");
        sellManualPriceField = new JTextField(existingInstrument != null && existingInstrument.getManualSellPrice() != null ?
                existingInstrument.getManualSellPrice().toString() : "");

        // Добавление компонентов
 //       add(new JLabel("Дата (YYYY-MM-DD):"));
 //       add(bookdateField);
        add(new JLabel("FIGI:"));
        add(figiField);
        add(new JLabel("Название:"));
        add(nameField);
        add(new JLabel("ISIN:"));
        add(isinField);
        add(new JLabel("Приоритет:"));
        add(priorityField);
        add(new JLabel("Кол-во покупки:"));
        add(buyQtyField);
        add(new JLabel("Цена покупки:"));
        add(buyPriceField);
        add(new JLabel("Моя цена покупки:"));
        add(buyManualPriceField);
        add(new JLabel("Кол-во продажи:"));
        add(sellQtyField);
        add(new JLabel("Цена продажи:"));
        add(sellPriceField);
        add(new JLabel("Моя цена продажи:"));
        add(sellManualPriceField);

        // Кнопки
        JButton saveButton = new JButton("Сохранить");
        saveButton.addActionListener(e -> saveInstrument());

        JButton cancelButton = new JButton("Отмена");
        cancelButton.addActionListener(e -> dispose());

        add(saveButton);
        add(cancelButton);
    }

    private void saveInstrument() {
        try {
            Instrument instrument = existingInstrument != null ? existingInstrument : new Instrument();

            instrument.setFigi(figiField.getText().isEmpty() ? null : figiField.getText());
            instrument.setName(nameField.getText());
            instrument.setIsin(isinField.getText());
            instrument.setPriority(Integer.parseInt(priorityField.getText()));

            if (!buyQtyField.getText().isEmpty()) {
                instrument.setBuyQuantity(Integer.parseInt(buyQtyField.getText()));
            }
            if (!buyPriceField.getText().isEmpty()) {
                instrument.setBuyPrice(new BigDecimal(buyPriceField.getText()));
            }
            if (!buyManualPriceField.getText().isEmpty()) {
                instrument.setManualBuyPrice(new BigDecimal(buyManualPriceField.getText()));
            }
            if (!sellQtyField.getText().isEmpty()) {
                instrument.setSellQuantity(Integer.parseInt(sellQtyField.getText()));
            }
            if (!sellPriceField.getText().isEmpty()) {
                instrument.setSellPrice(new BigDecimal(sellPriceField.getText()));
            }
            if (!sellManualPriceField.getText().isEmpty()) {
                instrument.setManualSellPrice(new BigDecimal(sellManualPriceField.getText()));
            }

            log.debug("buy_price: {} (null={})", instrument.getBuyPrice(), instrument.getBuyPrice() == null);
            log.debug("manual_buy_price: {} (null={})", instrument.getManualBuyPrice(), instrument.getManualBuyPrice() == null);

            if (existingInstrument == null) {
                log.info("💾 Сохранение инструмента: {}", instrument.getName());
                repository.save(instrument);
                JOptionPane.showMessageDialog(this, "Инструмент успешно добавлен!", "Успех", JOptionPane.INFORMATION_MESSAGE);
            } else {
                log.info("💾 Обновление инструмента ID: {}, Name: {}", instrument.getId(), instrument.getName());
                repository.update(instrument);
                JOptionPane.showMessageDialog(this, "Инструмент успешно обновлён!", "Успех", JOptionPane.INFORMATION_MESSAGE);
            }

            if (onSaveCallback != null) {
                onSaveCallback.run();
            }
            dispose();

        } catch (Exception ex) {
            log.error("❌ Ошибка сохранения инструмента", ex);
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void setOnSaveCallback(Runnable callback) {
        this.onSaveCallback = callback;
    }
}
