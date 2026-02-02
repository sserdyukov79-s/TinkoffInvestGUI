package com.algotrading.tinkoffinvestgui.ui.utils;

import javax.swing.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Утилита для выполнения асинхронных задач в Swing
 * Упрощает работу с SwingWorker и избавляет от дублирования кода
 */
public class AsyncTask {
    
    private static final Logger log = LoggerFactory.getLogger(AsyncTask.class);
    
    /**
     * Выполнить задачу асинхронно с обработкой успеха и ошибок
     * 
     * @param task Задача для выполнения в фоновом потоке
     * @param onSuccess Callback при успешном выполнении
     * @param onError Callback при ошибке
     */
    public static <T> void execute(
            Callable<T> task,
            Consumer<T> onSuccess,
            Consumer<Exception> onError) {
        
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }
            
            @Override
            protected void done() {
                try {
                    T result = get();
                    SwingUtilities.invokeLater(() -> onSuccess.accept(result));
                } catch (Exception e) {
                    log.error("Ошибка выполнения асинхронной задачи", e);
                    SwingUtilities.invokeLater(() -> onError.accept(e));
                }
            }
        };
        worker.execute();
    }
    
    /**
     * Выполнить задачу асинхронно только с обработкой успеха
     * Ошибки логируются, но не отображаются пользователю
     */
    public static <T> void execute(Callable<T> task, Consumer<T> onSuccess) {
        execute(task, onSuccess, e -> log.error("Ошибка: {}", e.getMessage()));
    }
    
    /**
     * Выполнить задачу без результата (Runnable)
     */
    public static void execute(
            Runnable task,
            Runnable onSuccess,
            Consumer<Exception> onError) {
        
        execute(
            () -> {
                task.run();
                return null;
            },
            result -> onSuccess.run(),
            onError
        );
    }
}
