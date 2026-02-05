package com.algotrading.tinkoffinvestgui.ui.dialogs;

import com.algotrading.tinkoffinvestgui.service.BondStrategyBacktestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;

/**
 * Диалог параметров бэктестинга
 */
public class BacktestDialog extends JDialog {
    
    private static final Logger log = LoggerFactory.getLogger(BacktestDialog.class);
    
    private JTextField startDateField;
    private JTextField endDateField;
    private JComboBox<String> currencyCombo;
    private JCheckBox amortCheckbox;
    private JTextField minDaysField;
    private JTextField maxMonthsField;
    private JCheckBox dlongCheckbox;
    private JCheckBox riskCheckbox;
    private JTextField volumeField;
    
    private LocalDate startDate;
    private LocalDate endDate;
    private BondStrategyBacktestService.BacktestFilters filters;
    private boolean confirmed = false;

    public BacktestDialog(JFrame parent) {
        super(parent, "Параметры бэктестинга", true);
        setSize(500, 550);
        setLocationRelativeTo(parent);
        initializeUI();
    }

    private void initializeUI() {
        log.info("🧪 Открытие диалога параметров бэктестинга");
        
        setLayout(new BorderLayout(15, 15));
        
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
        
        add(descArea, BorderLayout.NORTH);
        
        JPanel paramsPanel = new JPanel(new GridLayout(9, 2, 10, 10));
        paramsPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        
        JLabel startDateLabel = new JLabel("Дата начала:");
        startDateField = new JTextField(LocalDate.now().minusYears(1).toString());
        
        JLabel endDateLabel = new JLabel("Дата окончания:");
        endDateField = new JTextField(LocalDate.now().toString());
        
        JLabel currencyLabel = new JLabel("Валюта:");
        currencyCombo = new JComboBox<>(new String[]{"RUB", "USD", "EUR", "CNY"});
        currencyCombo.setSelectedItem("RUB");
        
        JLabel amortLabel = new JLabel("Без амортизации:");
        amortCheckbox = new JCheckBox();
        amortCheckbox.setSelected(true);
        
        JLabel minDaysLabel = new JLabel("Мин. дней до погашения:");
        minDaysField = new JTextField("3");
        
        JLabel maxMonthsLabel = new JLabel("Макс. месяцев до погашения:");
        maxMonthsField = new JTextField("15");
        
        JLabel dlongLabel = new JLabel("Dlong > 0:");
        dlongCheckbox = new JCheckBox();
        dlongCheckbox.setSelected(true);
        
        JLabel riskLabel = new JLabel("Исключить высокий риск:");
        riskCheckbox = new JCheckBox();
        riskCheckbox.setSelected(true);
        
        JLabel volumeLabel = new JLabel("Мин. ср.дневн. объём (лот):");
        volumeField = new JTextField("2000");
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
        
        add(paramsPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton runButton = new JButton("Запустить");
        runButton.setFont(new Font("Arial", Font.BOLD, 12));
        
        JButton cancelButton = new JButton("Отмена");
        
        runButton.addActionListener(e -> onRun());
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(runButton);
        buttonPanel.add(cancelButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private void onRun() {
        try {
            startDate = LocalDate.parse(startDateField.getText());
            endDate = LocalDate.parse(endDateField.getText());
            
            filters = new BondStrategyBacktestService.BacktestFilters();
            filters.currency = (String) currencyCombo.getSelectedItem();
            filters.withoutAmortization = amortCheckbox.isSelected();
            filters.minDaysToMaturity = Integer.parseInt(minDaysField.getText());
            filters.maxMonthsToMaturity = Integer.parseInt(maxMonthsField.getText());
            filters.requireDlong = dlongCheckbox.isSelected();
            filters.excludeHighRisk = riskCheckbox.isSelected();
            filters.minAvgDailyVolume = Double.parseDouble(volumeField.getText());
            
            confirmed = true;
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public Object[] showDialog() {
        setVisible(true);
        return confirmed ? new Object[]{startDate, endDate, filters} : null;
    }
}
