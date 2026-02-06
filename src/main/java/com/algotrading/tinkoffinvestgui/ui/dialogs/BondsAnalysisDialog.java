package com.algotrading.tinkoffinvestgui.ui.dialogs;

import com.algotrading.tinkoffinvestgui.service.BondsAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Диалог параметров анализа облигаций
 */
public class BondsAnalysisDialog extends JDialog {
    
    private static final Logger log = LoggerFactory.getLogger(BondsAnalysisDialog.class);
    
    private JComboBox<String> currencyCombo;
    private JCheckBox amortCheckbox;
    private JTextField minDaysField;
    private JTextField maxMonthsField;
    private JCheckBox dlongCheckbox;
    private JCheckBox riskCheckbox;
    private JTextField volumeField;
    
    private BondsAnalysisService.BondsFilterCriteria result;
    private boolean confirmed = false;

    public BondsAnalysisDialog(JFrame parent) {
        super(parent, "Параметры анализа облигаций", true);
        setSize(450, 450);
        setLocationRelativeTo(parent);
        initializeUI();
    }

    private void initializeUI() {
        log.info("🔍 Открытие диалога параметров анализа облигаций");
        
        setLayout(new BorderLayout(10, 10));
        
        JPanel filtersPanel = new JPanel(new GridLayout(9, 2, 10, 10));
        filtersPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JLabel currencyLabel = new JLabel("Валюта:");
        currencyCombo = new JComboBox<>(new String[]{"RUB", "USD", "EUR", "CNY"});
        
        JLabel amortLabel = new JLabel("Без амортизации:");
        amortCheckbox = new JCheckBox();
        amortCheckbox.setSelected(true);
        
        JLabel minDaysLabel = new JLabel("Мин. дней до погашения:");
        minDaysField = new JTextField("3");
        
        JLabel maxMonthsLabel = new JLabel("Макс. месяцев до погашения:");
        maxMonthsField = new JTextField("15");
        
        JLabel dlongLabel = new JLabel("Требовать Dlong:");
        dlongCheckbox = new JCheckBox();
        dlongCheckbox.setSelected(true);
        
        JLabel riskLabel = new JLabel("Исключить высокий риск:");
        riskCheckbox = new JCheckBox();
        riskCheckbox.setSelected(true);
        
        JLabel volumeLabel = new JLabel("Мин. ср.дневн. объём (лотов):");
        volumeField = new JTextField("2000");
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
        
        JLabel infoLabel = new JLabel("Анализ использует период 4 месяца из БД");
        infoLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        filtersPanel.add(new JLabel(""));
        filtersPanel.add(infoLabel);
        
        add(filtersPanel, BorderLayout.CENTER);
        
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton startButton = new JButton("Запустить");
        JButton cancelButton = new JButton("Отмена");
        
        startButton.addActionListener(e -> onStart());
        cancelButton.addActionListener(e -> dispose());
        
        buttonsPanel.add(startButton);
        buttonsPanel.add(cancelButton);
        
        add(buttonsPanel, BorderLayout.SOUTH);
    }
    
    private void onStart() {
        try {
            result = new BondsAnalysisService.BondsFilterCriteria();
            result.setNominalCurrency((String) currencyCombo.getSelectedItem());
            result.setWithoutAmortization(amortCheckbox.isSelected());
            result.setMinDaysToMaturity(Integer.parseInt(minDaysField.getText()));
            result.setMaxMonthsToMaturity(Integer.parseInt(maxMonthsField.getText()));
            result.setRequireDlong(dlongCheckbox.isSelected());
            result.setExcludeHighRisk(riskCheckbox.isSelected());
            
            double minVolume = Double.parseDouble(volumeField.getText());
            result.setMinAvgDailyVolume(minVolume);
            
            confirmed = true;
            dispose();
        } catch (Exception ex) {
            log.error("❌ Ошибка параметров анализа", ex);
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public BondsAnalysisService.BondsFilterCriteria showDialog() {
        setVisible(true);
        return confirmed ? result : null;
    }
}
