package com.algotrading.tinkoffinvestgui.ui.dialogs;

import com.algotrading.tinkoffinvestgui.service.CandlesExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.CandleInterval;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;

/**
 * Диалог экспорта свечей в CSV
 */
public class CandlesExportDialog extends JDialog {
    
    private static final Logger log = LoggerFactory.getLogger(CandlesExportDialog.class);
    
    private JTextField figiField;
    private JComboBox<String> intervalCombo;
    private JTextField fromField;
    private JTextField toField;
    private JButton exportButton;

    public CandlesExportDialog(JFrame parent) {
        super(parent, "Экспорт свечей в CSV", true);
        setLayout(new BorderLayout(10, 10));
        setSize(500, 350);
        setLocationRelativeTo(parent);
        initializeUI();
    }

    private void initializeUI() {
        log.info("📥 Открытие диалога экспорта свечей");
        
        JPanel inputPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JLabel figiLabel = new JLabel("FIGI:");
        figiField = new JTextField();
        figiField.setToolTipText("Например: BBG004730N88");
        
        JLabel intervalLabel = new JLabel("Интервал:");
        String[] intervals = {"1 мин", "1 час", "1 день", "1 неделя", "1 месяц", "15 мин", "5 мин", "1 квартал"};
        intervalCombo = new JComboBox<>(intervals);
        intervalCombo.setSelectedItem("1 день");
        
        JLabel fromLabel = new JLabel("От (дата):");
        LocalDate defaultFrom = LocalDate.now().minusMonths(4).minusDays(1);
        fromField = new JTextField(defaultFrom.toString());
        fromField.setToolTipText("Формат: YYYY-MM-DD");
        
        JLabel toLabel = new JLabel("До (дата):");
        LocalDate defaultTo = LocalDate.now().minusDays(1);
        toField = new JTextField(defaultTo.toString());
        toField.setToolTipText("Формат: YYYY-MM-DD");
        
        inputPanel.add(figiLabel);
        inputPanel.add(figiField);
        inputPanel.add(intervalLabel);
        inputPanel.add(intervalCombo);
        inputPanel.add(fromLabel);
        inputPanel.add(fromField);
        inputPanel.add(toLabel);
        inputPanel.add(toField);
        
        add(inputPanel, BorderLayout.CENTER);
        
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
        
        add(infoPanel, BorderLayout.NORTH);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        exportButton = new JButton("Экспортировать");
        exportButton.setFont(new Font("Arial", Font.BOLD, 12));
        exportButton.addActionListener(e -> onExport());
        
        JButton cancelButton = new JButton("Отмена");
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(exportButton);
        buttonPanel.add(cancelButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private void onExport() {
        try {
            String figi = figiField.getText().trim();
            if (figi.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Введите FIGI!", "Ошибка", JOptionPane.ERROR_MESSAGE);
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
                        JOptionPane.showMessageDialog(CandlesExportDialog.this,
                            String.format("Свечи успешно экспортированы!\n%s", filePath),
                            "Успех", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } catch (Exception ex) {
                        log.error("❌ Ошибка экспорта свечей", ex);
                        JOptionPane.showMessageDialog(CandlesExportDialog.this, 
                            ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        exportButton.setEnabled(true);
                        exportButton.setText("Экспортировать");
                    }
                }
            };
            
            worker.execute();
            
        } catch (Exception ex) {
            log.error("❌ Ошибка параметров экспорта", ex);
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }
}
