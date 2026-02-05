package com.algotrading.tinkoffinvestgui.ui.dialogs;

import com.algotrading.tinkoffinvestgui.repository.ParametersRepository;
import com.algotrading.tinkoffinvestgui.service.BondStrategyCalculator;
import com.algotrading.tinkoffinvestgui.service.BondsAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Диалог результатов анализа облигаций
 */
public class AnalysisResultsDialog extends JDialog {
    
    private static final Logger log = LoggerFactory.getLogger(AnalysisResultsDialog.class);
    
    private final List<BondsAnalysisService.BondAnalysisResult> results;
    private final ParametersRepository paramsRepo;

    public AnalysisResultsDialog(JFrame parent, List<BondsAnalysisService.BondAnalysisResult> results) {
        super(parent, "Результаты анализа облигаций", false);
        this.results = results;
        this.paramsRepo = new ParametersRepository();
        
        setSize(1800, 800);
        setLocationRelativeTo(parent);
        initializeUI();
    }

    private void initializeUI() {
        log.info("📊 Отображение результатов анализа: {} облигаций", results.size());
        
        setLayout(new BorderLayout(10, 10));
        
        String[] columns = {
            "Тикер", "Название", "FIGI", "Валюта", "Погашение", "Dlong", "Риск",
            "Волатильность,%", "Ср.дн.объём (лот)", "Тек. цена", "Средняя цена",
            "Изменение, %", "Тренд", "Цена покупки", "Цена продажи", "Скидка,%",
            "Прибыль без ком.,%", "Чистая прибыль,%", "Комиссии,₽",
            "Комиссии,% от покупки", "Балл"
        };
        
        Object[][] data = new Object[results.size()][columns.length];
        for (int i = 0; i < results.size(); i++) {
            BondsAnalysisService.BondAnalysisResult r = results.get(i);
            ParametersRepository.StrategyParameters params = paramsRepo.getStrategyParameters();
            BondStrategyCalculator.StrategyRecommendation strategy = 
                BondStrategyCalculator.calculatePrices(r, params);
            
            int col = 0;
            data[i][col++] = r.getTicker();
            data[i][col++] = r.getName();
            data[i][col++] = r.getFigi();
            data[i][col++] = r.getNominalCurrency();
            data[i][col++] = r.getMaturityDate() != null ? r.getMaturityDate().toString() : "-";
            data[i][col++] = String.format("%.2f", r.getDlong());
            data[i][col++] = r.getRiskLevel();
            data[i][col++] = String.format("%.4f%%", (r.getVolatility() / r.getAvgPrice()) * 100);
            data[i][col++] = String.format("%.0f", r.getAvgDailyVolume());
            data[i][col++] = String.format("%.2f₽", r.getCurrentPrice());
            data[i][col++] = String.format("%.2f₽", r.getAvgPrice());
            data[i][col++] = String.format("%.2f%%", r.getPriceChangePercent());
            data[i][col++] = String.format("%.4f", r.getTrend());
            data[i][col++] = strategy.getBuyPrice();
            data[i][col++] = strategy.getSellPrice();
            data[i][col++] = String.format("%.2f%%", strategy.getDiscountPercent());
            data[i][col++] = String.format("%.2f%%", strategy.getProfitWithoutCommission());
            data[i][col++] = String.format("%.2f%%", strategy.getNetProfit());
            data[i][col++] = String.format("%.2f₽", strategy.getTotalCommissions());
            
            double commissionPercent = (strategy.getTotalCommissions() / strategy.getBuyPrice().doubleValue()) * 100;
            data[i][col++] = String.format("%.3f%%", commissionPercent);
            data[i][col++] = String.format("%.2f", r.getScore());
        }
        
        JTable table = new JTable(new DefaultTableModel(data, columns));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow >= 0) {
                    BondsAnalysisService.BondAnalysisResult analysis = results.get(selectedRow);
                    ParametersRepository.StrategyParameters params = paramsRepo.getStrategyParameters();
                    BondStrategyCalculator.StrategyRecommendation strategy = 
                        BondStrategyCalculator.calculatePrices(analysis, params);
                    showStrategyDetails(analysis, strategy);
                }
            }
        });
        
        addTableCopyMenu(table);
        
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
        
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel(String.format("Найдено облигаций: %d | Клик для просмотра деталей", 
            results.size()));
        infoLabel.setFont(new Font("Arial", Font.BOLD, 13));
        infoPanel.add(infoLabel);
        
        add(infoPanel, BorderLayout.NORTH);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton closeButton = new JButton("Закрыть");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
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
        addParamRow(paramsPanel, "Волатильность:", String.format("%.4f (%.2f%%)", 
            analysis.getVolatility(), strategy.getVolatilityPercent()));
        addParamRow(paramsPanel, "Тренд:", String.format("%.4f (%.2f%%)", 
            analysis.getTrend(), analysis.getTrend() * 100));
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
        addParamRow(paramsPanel, "Чистая прибыль:", String.format("%.2f₽ (%.2f%%)", 
            strategy.getNetProfit(), strategy.getProfitPercent()));
        
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
    }
    
    private String getTableDataWithHeaders(JTable table) {
        StringBuilder sb = new StringBuilder();
        
        int columnCount = table.getColumnCount();
        for (int col = 0; col < columnCount; col++) {
            sb.append(table.getColumnName(col));
            if (col < columnCount - 1) {
                sb.append("\t");
            }
        }
        sb.append("\n");
        
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
    
    private void copyToClipboard(String text) {
        java.awt.datatransfer.StringSelection selection = 
            new java.awt.datatransfer.StringSelection(text);
        java.awt.Toolkit.getDefaultToolkit()
            .getSystemClipboard()
            .setContents(selection, selection);
    }
}
