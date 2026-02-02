package com.algotrading.tinkoffinvestgui.util;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Утилита для упрощения работы со Swing
 */
public class SwingUtils {
    
    /**
     * Показать сообщение об ошибке
     */
    public static void showError(Component parent, String title, Exception error) {
        String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }
    
    /**
     * Показать информационное сообщение
     */
    public static void showInfo(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Показать диалог подтверждения
     */
    public static boolean showConfirm(Component parent, String title, String message) {
        int result = JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION);
        return result == JOptionPane.YES_OPTION;
    }
    
    /**
     * Создать кнопку с действием
     */
    public static JButton createButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        return button;
    }
    
    /**
     * Выполнить задачу в фоновом потоке
     */
    public static <T> void runInBackground(
            Supplier<T> backgroundTask,
            Consumer<T> onSuccess,
            Consumer<Exception> onError) {
        
        SwingWorker<T, Void> worker = new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return backgroundTask.get();
            }
            
            @Override
            protected void done() {
                try {
                    T result = get();
                    onSuccess.accept(result);
                } catch (Exception e) {
                    onError.accept(e);
                }
            }
        };
        
        worker.execute();
    }
    
    /**
     * Выполнить код в Event Dispatch Thread
     */
    public static void runInEDT(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
    
    /**
     * Установить глобальный Look and Feel
     */
    public static void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
