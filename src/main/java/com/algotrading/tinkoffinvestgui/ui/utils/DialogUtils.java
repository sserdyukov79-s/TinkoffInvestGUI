package com.algotrading.tinkoffinvestgui.ui.utils;

import javax.swing.*;
import java.awt.*;

/**
 * Утилиты для работы с диалоговыми окнами
 */
public class DialogUtils {
    
    /**
     * Показать сообщение об ошибке
     */
    public static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(
            parent,
            message,
            "Ошибка",
            JOptionPane.ERROR_MESSAGE
        );
    }
    
    /**
     * Показать сообщение об ошибке с заголовком
     */
    public static void showError(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(
            parent,
            message,
            title,
            JOptionPane.ERROR_MESSAGE
        );
    }
    
    /**
     * Показать сообщение об успехе
     */
    public static void showSuccess(Component parent, String message) {
        JOptionPane.showMessageDialog(
            parent,
            message,
            "Успех",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
    
    /**
     * Показать предупреждение
     */
    public static void showWarning(Component parent, String message) {
        JOptionPane.showMessageDialog(
            parent,
            message,
            "Предупреждение",
            JOptionPane.WARNING_MESSAGE
        );
    }
    
    /**
     * Показать диалог подтверждения
     * @return true если пользователь выбрал "Да"
     */
    public static boolean confirm(Component parent, String message) {
        return confirm(parent, "Подтверждение", message);
    }
    
    /**
     * Показать диалог подтверждения с заголовком
     * @return true если пользователь выбрал "Да"
     */
    public static boolean confirm(Component parent, String title, String message) {
        int result = JOptionPane.showConfirmDialog(
            parent,
            message,
            title,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        return result == JOptionPane.YES_OPTION;
    }
    
    /**
     * Создать диалог прогресса с неопределённым прогрессом
     */
    public static JDialog createProgressDialog(Component parent, String title, String message) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent), title, false);
        dialog.setSize(400, 150);
        dialog.setLocationRelativeTo(parent);
        dialog.setLayout(new BorderLayout(10, 10));
        
        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 14));
        label.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));
        
        dialog.add(label, BorderLayout.CENTER);
        dialog.add(progressBar, BorderLayout.SOUTH);
        
        return dialog;
    }
}
