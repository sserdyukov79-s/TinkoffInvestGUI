package com.algotrading.tinkoffinvestgui.ui.utils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Утилиты для работы с таблицами JTable
 */
public class TableUtils {
    
    private static final Logger log = LoggerFactory.getLogger(TableUtils.class);
    
    /**
     * Добавить контекстное меню копирования для таблицы
     */
    public static void addCopyMenu(JTable table) {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copyAllItem = new JMenuItem("Копировать всё (Excel формат)");
        copyAllItem.setFont(new Font("Arial", Font.BOLD, 12));
        
        copyAllItem.addActionListener(e -> {
            try {
                String data = getTableDataWithHeaders(table);
                copyToClipboard(data);
                
                Component parent = SwingUtilities.getWindowAncestor(table);
                JOptionPane.showMessageDialog(
                    parent,
                    String.format("Скопировано %d строк + заголовки.\n\nВставьте в Excel (Ctrl+V)",
                        table.getRowCount()),
                    "Копирование",
                    JOptionPane.INFORMATION_MESSAGE
                );
                
                log.info("📋 Таблица скопирована: {} строк", table.getRowCount());
            } catch (Exception ex) {
                log.error("❌ Ошибка копирования таблицы", ex);
                Component parent = SwingUtilities.getWindowAncestor(table);
                JOptionPane.showMessageDialog(
                    parent,
                    ex.getMessage(),
                    "Ошибка",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });
        
        popupMenu.add(copyAllItem);
        table.setComponentPopupMenu(popupMenu);
        
        // Показывать меню по правой кнопке мыши
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
     * Получить данные таблицы с заголовками в формате Excel (TAB)
     */
    public static String getTableDataWithHeaders(JTable table) {
        StringBuilder sb = new StringBuilder();
        
        // Заголовки
        int columnCount = table.getColumnCount();
        for (int col = 0; col < columnCount; col++) {
            sb.append(table.getColumnName(col));
            if (col < columnCount - 1) {
                sb.append("\t");
            }
        }
        sb.append("\n");
        
        // Данные
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
     * Копировать текст в буфер обмена
     */
    public static void copyToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit()
            .getSystemClipboard()
            .setContents(selection, selection);
    }
    
    /**
     * Создать пустую модель таблицы
     */
    public static DefaultTableModel createEmptyModel(String[] columns) {
        return new DefaultTableModel(new Object[][]{}, columns);
    }
    
    /**
     * Очистить таблицу
     */
    public static void clearTable(JTable table) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
    }
}
