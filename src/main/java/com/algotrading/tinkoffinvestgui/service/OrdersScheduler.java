package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.repository.ParametersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Планировщик автоматического выставления заявок по расписанию
 */
public class OrdersScheduler {
    private static final Logger log = LoggerFactory.getLogger(OrdersScheduler.class);

    private final ParametersRepository parametersRepository;
    private final ScheduledExecutorService scheduler;
    private final Runnable ordersTask;

    private LocalDate lastExecutionDate = null;
    private volatile boolean isRunning = false;

    public OrdersScheduler(ParametersRepository parametersRepository, Runnable ordersTask) {
        this.parametersRepository = parametersRepository;
        this.ordersTask = ordersTask;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    /**
     * Запуск планировщика
     */
    public void start() {
        if (isRunning) {
            log.warn("⚠️ Планировщик уже запущен");
            return;
        }

        isRunning = true;
        log.info("🚀 Запуск планировщика автоматических заявок");

        // Проверяем сразу при старте
        checkAndExecuteOrders();

        // Периодическая проверка каждую минуту
        scheduler.scheduleAtFixedRate(
                this::checkAndExecuteOrders,
                1, 1, TimeUnit.MINUTES
        );
    }

    /**
     * Остановка планировщика
     */
    public void stop() {
        log.info("🛑 Остановка планировщика автоматических заявок");
        isRunning = false;
        scheduler.shutdown();
    }

    /**
     * Проверяет условия и выполняет выставление заявок
     */
    private void checkAndExecuteOrders() {
        try {
            // 1. Проверяем, что сегодня будний день
            if (!isWeekday()) {
                log.debug("📅 Сегодня выходной, заявки не выставляем");
                return;
            }

            // 2. Проверяем, что заявки ещё не выставлялись сегодня
            LocalDate today = LocalDate.now();
            if (today.equals(lastExecutionDate)) {
                log.debug("✅ Заявки уже выставлены сегодня");
                return;
            }

            // 3. Получаем время начала торгов из БД
            String startTimeStr = parametersRepository.getParameter("start_time");
            if (startTimeStr == null || startTimeStr.isEmpty()) {
                log.warn("⚠️ Параметр start_time не найден в БД");
                return;
            }

            LocalTime startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm:ss"));
            LocalTime now = LocalTime.now();

            // 4. Проверяем, что текущее время >= start_time
            if (now.isBefore(startTime)) {
                long minutesUntilStart = Duration.between(now, startTime).toMinutes();
                log.debug("⏰ До начала торгов осталось {} минут (start_time: {})", minutesUntilStart, startTimeStr);
                return;
            }

            // 5. Все условия выполнены - выставляем заявки
            log.info("🎯 Условия выполнены! Выставляем заявки автоматически (start_time: {})", startTimeStr);
            executeOrders();
            lastExecutionDate = today;

        } catch (Exception e) {
            log.error("❌ Ошибка при проверке условий выставления заявок: {}", e.getMessage(), e);
        }
    }

    /**
     * Проверяет, является ли сегодня будним днём
     */
    private boolean isWeekday() {
        // ОРИГИНАЛЬНЫЙ КОД (закомментирован) для тестирования:
         DayOfWeek today = LocalDate.now().getDayOfWeek();
         return today != DayOfWeek.SATURDAY && today != DayOfWeek.SUNDAY;

        // ⚠️ ДЛЯ ТЕСТИРОВАНИЯ: всегда разрешаем выставление заявок
       // return true;
    }

    /**
     * Выполняет выставление заявок
     */
    private void executeOrders() {
        try {
            log.info("📤 Выполняем автоматическое выставление заявок");
            ordersTask.run();
            log.info("✅ Заявки успешно выставлены автоматически");
        } catch (Exception e) {
            log.error("❌ Ошибка при выставлении заявок: {}", e.getMessage(), e);
        }
    }

    /**
     * Получить дату последнего выполнения
     */
    public LocalDate getLastExecutionDate() {
        return lastExecutionDate;
    }

    /**
     * Проверить, запущен ли планировщик
     */
    public boolean isRunning() {
        return isRunning;
    }
}
