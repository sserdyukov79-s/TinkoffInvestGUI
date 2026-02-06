package com.algotrading.tinkoffinvestgui.ui.dialogs;

import com.algotrading.tinkoffinvestgui.service.BondStrategyBacktestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Диалог отчета бэктестинга
 */
public class BacktestReportDialog extends JDialog {
    
    private static final Logger log = LoggerFactory.getLogger(BacktestReportDialog.class);
    
    private final BondStrategyBacktestService.BacktestReport report;

    public BacktestReportDialog(JFrame parent, BondStrategyBacktestService.BacktestReport report) {
        super(parent, "Результаты бэктестинга", false);
        this.report = report;
        
        setSize(1400, 800);
        setLocationRelativeTo(parent);
        initializeUI();
    }

    private void initializeUI() {
        log.info("📊 Отображение отчёта бэктестинга: {} облигаций, {} сделок",
            report.getTotalBonds(), report.getTotalTrades());
        
        setLayout(new BorderLayout(10, 10));
        
        JPanel statsPanel = new JPanel(new GridLayout(2, 5, 15, 10));
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
        
        add(statsPanel, BorderLayout.NORTH);
        
        String[] columns = {
            "Тикер", "Название", "FIGI", "Dlong", "Ср.дн.объём", "Сделок",
            "Прибыльных", "Убыточных", "Винрейт,%", "Общ.приб. БЕЗ ком.,₽",
            "Общ.чист.приб.,₽", "Ср.приб. БЕЗ ком.,₽", "Ср.чист.приб.,₽",
            "Ср.чист.приб.,%", "Ср.удержание,дн"
        };
        
        List<BondStrategyBacktestService.BondBacktestResult> results = report.getBondResults();
        Object[][] data = new Object[results.size()][columns.length];
        
        for (int i = 0; i < results.size(); i++) {
            BondStrategyBacktestService.BondBacktestResult r = results.get(i);
            data[i][0] = r.getTicker();
            data[i][1] = r.getName();
            data[i][2] = r.getFigi();
            data[i][3] = String.format("%.2f", r.getDlong());
            data[i][4] = String.format("%.0f", r.getAvgDailyVolume());
            data[i][5] = r.getTotalTrades();
            data[i][6] = r.getProfitableTrades();
            data[i][7] = r.getLosingTrades();
            data[i][8] = String.format("%.1f%%", r.getWinRate());
            data[i][9] = String.format("%.2f₽", r.getTotalProfitBeforeCommission());
            data[i][10] = String.format("%.2f₽", r.getTotalProfit());
            data[i][11] = String.format("%.2f₽", r.getAvgProfitBeforeCommission());
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
        add(scrollPane, BorderLayout.CENTER);
        
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("Клик на строку для просмотра сделок по облигации");
        infoLabel.setFont(new Font("Arial", Font.BOLD, 12));
        infoPanel.add(infoLabel);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(infoPanel, BorderLayout.NORTH);
        
        JButton closeButton = new JButton("Закрыть");
        closeButton.addActionListener(e -> dispose());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(closeButton);
        
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private void addStatLabel(JPanel panel, String label, String value) {
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(new Font("Arial", Font.BOLD, 11));
        
        JLabel valueComp = new JLabel(value);
        valueComp.setFont(new Font("Arial", Font.PLAIN, 11));
        
        panel.add(labelComp);
        panel.add(valueComp);
    }
    
    private void showBondTradesDialog(BondStrategyBacktestService.BondBacktestResult bondResult) {
        JDialog dialog = new JDialog(this,
            String.format("Сделки: %s (%s)", bondResult.getTicker(), bondResult.getName()),
            true);
        dialog.setSize(1200, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        
        String[] columns = {
            "Дата покупки", "Цена покупки", "Волатильность",
            "Дата продажи", "Цена продажи", "Удержание,дн",
            "Прибыль,₽", "Прибыль,%"
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
