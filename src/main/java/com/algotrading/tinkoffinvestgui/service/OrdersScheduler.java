package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.repository.InstrumentsRepository;
import com.algotrading.tinkoffinvestgui.repository.ParametersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Планировщик ежедневных операций:
 * 1. Копирование инструментов на новую дату (DB скрипт)
 * 2. Расчёт цен покупки/продажи по алгоритму
 * 3. Выставление заявок
 */
public class OrdersScheduler {
    private static final Logger log = LoggerFactory.getLogger(OrdersScheduler.class);

    private final ParametersRepository parametersRepository;
    private final InstrumentsRepository instrumentsRepository;
    private final DailyDataPreparationService dataPreparationService;
    private final Runnable ordersTask;
    private final ScheduledExecutorService scheduler;

    private volatile boolean isRunning = false;
    private LocalDate lastExecutionDate = null;
    private Runnable tableRefreshCallback;

    public OrdersScheduler(ParametersRepository parametersRepository,
                           InstrumentsRepository instrumentsRepository,
                           Runnable ordersTask, Runnable tableRefreshCallback) {
        this.parametersRepository = parametersRepository;
        this.instrumentsRepository = instrumentsRepository;
        this.ordersTask = ordersTask;
        this.dataPreparationService = new DailyDataPreparationService(instrumentsRepository);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.tableRefreshCallback = tableRefreshCallback;
    }

    /**
     * Запускает планировщик (проверка каждую минуту)
     */
    public void start() {
        if (isRunning) {
            log.warn("⚠️  Планировщик уже запущен");
            return;
        }

        isRunning = true;
        log.info("🚀 Планировщик запущен");

        // Выполняем сразу при старте приложения
        checkAndExecute();

        // Затем проверяем каждую минуту
        scheduler.scheduleAtFixedRate(
                this::checkAndExecute,
                1, 1, TimeUnit.MINUTES
        );
    }

    public void stop() {
        log.info("🛑 Остановка планировщика");
        isRunning = false;
        scheduler.shutdown();
    }

    /**
     * Проверяет условия и запускает полный цикл при необходимости
     */
    private void checkAndExecute() {
        try {
            // 1. Проверка: рабочий день
                if (!isWeekday()) {
                log.debug("📅 Сегодня выходной, пропускаем");
                return;
            }

            // 2. Проверка: уже выполнялось сегодня
            LocalDate today = LocalDate.now();
            if (today.equals(lastExecutionDate)) {
                log.debug("✅ Операции уже выполнены сегодня");
                return;
            }

            // 3. Проверка: время старта
            String startTimeStr = parametersRepository.getParameterValue("start_time");
            if (startTimeStr == null || startTimeStr.isEmpty()) {
                log.warn("⚠️  Параметр 'start_time' не задан в БД");
                return;
            }

            LocalTime startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm:ss"));
            LocalTime now = LocalTime.now();

            // 4. Если время ещё не пришло, ждём
            if (now.isBefore(startTime)) {
                long minutesUntil = Duration.between(now, startTime).toMinutes();
                log.debug("⏰ До времени старта {} осталось {} минут", startTimeStr, minutesUntil);
                return;
            }

            // 5. Все проверки пройдены — запускаем
            log.info("🎯 Время пришло! starttime={}", startTimeStr);
            executeFullCycle();
            lastExecutionDate = today;

        } catch (Exception e) {
            log.error("❌ Ошибка в планировщике", e);
        }
    }

    /**
     * Выполняет полный цикл операций в правильном порядке:
     * 1. Подготовка данных (DB скрипт + расчёт цен)
     * 2. Выставление заявок
     */
    private void executeFullCycle() {
        try {
            log.info("═══════════════════════════════════════════════════════");
            log.info("🔄 ЗАПУСК ПОЛНОГО ЦИКЛА ЕЖЕДНЕВНЫХ ОПЕРАЦИЙ");
            log.info("═══════════════════════════════════════════════════════");

            // Шаг 1: Подготовка данных (DB скрипт + расчёт цен)
            log.info("📋 ШАГ 1: Подготовка данных");
            boolean dataPreparationSuccess = dataPreparationService.prepareDailyData();

            if (!dataPreparationSuccess) {
                log.error("❌ Ошибка подготовки данных, выставление заявок отменено");
                return;
            }

            // ✅ Обновляем таблицу инструментов в GUI
            if (tableRefreshCallback != null) {
                tableRefreshCallback.run();
            }

            // Пауза перед выставлением заявок
            Thread.sleep(2000);

            // Шаг 2: Выставление заявок
            log.info("═══════════════════════════════════════════════════════");
            log.info("📤 ШАГ 2: Выставление заявок");
            log.info("═══════════════════════════════════════════════════════");

            ordersTask.run();

            log.info("═══════════════════════════════════════════════════════");
            log.info("✅ ПОЛНЫЙ ЦИКЛ ЗАВЕРШЁН УСПЕШНО");
            log.info("═══════════════════════════════════════════════════════");

        } catch (Exception e) {
            log.error("❌ Ошибка при выполнении полного цикла", e);
        }
    }

    private boolean isWeekday() {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        return today != DayOfWeek.SATURDAY && today != DayOfWeek.SUNDAY;
    }

    public LocalDate getLastExecutionDate() {
        return lastExecutionDate;
    }

    public boolean isRunning() {
        return isRunning;
    }
}
