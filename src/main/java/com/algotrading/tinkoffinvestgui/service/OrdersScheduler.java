package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.repository.InstrumentsRepository;
import com.algotrading.tinkoffinvestgui.repository.OrdersRepository;
import com.algotrading.tinkoffinvestgui.repository.ParametersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Планировщик:
 * 1) Ежедневно в заданное время запускает подготовку данных и выставление BUY-заявок.
 * 2) В фоне каждые N секунд запускает OrderTracker для отслеживания статусов и Stop-on-Fill.
 */
public class OrdersScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrdersScheduler.class);

    private final ParametersRepository parametersRepository;
    private final InstrumentsRepository instrumentsRepository;
    private final OrdersRepository ordersRepository;
    private final DailyDataPreparationService dataPreparationService;
    private final OrderTracker orderTracker;
    private final Runnable ordersTask;
    private final ScheduledExecutorService scheduler;

    private volatile boolean isRunning = false;
    private LocalDate lastExecutionDate = null;
    private Runnable tableRefreshCallback;

    private LocalTime dailyExecutionTime = LocalTime.of(9, 0);
    private int orderCheckIntervalSeconds = 5;

    public OrdersScheduler(ParametersRepository parametersRepository,
                           InstrumentsRepository instrumentsRepository,
                           OrdersRepository ordersRepository,
                           Runnable ordersTask,
                           Runnable tableRefreshCallback) {

        this.parametersRepository = parametersRepository;
        this.instrumentsRepository = instrumentsRepository;
        this.ordersRepository = ordersRepository;
        this.ordersTask = ordersTask;
        this.tableRefreshCallback = tableRefreshCallback;

        this.dataPreparationService = new DailyDataPreparationService(instrumentsRepository);
        this.scheduler = Executors.newScheduledThreadPool(2);

        String accountId = getActiveAccountId();
        this.orderTracker = new OrderTracker(ordersRepository, instrumentsRepository, accountId);

        log.info("OrdersScheduler инициализирован с OrderTracker для аккаунта {}", accountId);
    }

    public void start() {
        if (isRunning) {
            log.warn("OrdersScheduler уже запущен");
            return;
        }

        isRunning = true;

        log.info("Запуск OrdersScheduler. Ежедневное время: {}, интервал проверки заявок: {} сек",
                dailyExecutionTime, orderCheckIntervalSeconds);

        scheduleDailyTask();
        scheduleOrderMonitoring();
    }

    private void scheduleDailyTask() {
        long initialDelay = calculateInitialDelay();

        log.info("Ежедневная задача запланирована на {} (через {} сек)",
                dailyExecutionTime, initialDelay);

        scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        executeDailyTask();
                    } catch (Exception e) {
                        log.error("Ошибка в ежедневной задаче", e);
                    }
                },
                initialDelay,
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS
        );
    }

    private void scheduleOrderMonitoring() {
        log.info("Мониторинг заявок запланирован: каждые {} сек", orderCheckIntervalSeconds);

        scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        orderTracker.checkAndProcessPendingOrders();
                    } catch (Exception e) {
                        log.error("Ошибка мониторинга заявок", e);
                    }
                },
                5,
                orderCheckIntervalSeconds,
                TimeUnit.SECONDS
        );
    }

    private void executeDailyTask() {
        LocalDate today = LocalDate.now();
/*
        if (!isTradeDay(today)) {
            log.info("Сегодня не торговый день, ежедневная задача пропущена");
            return;
        }
*/
        if (lastExecutionDate != null && lastExecutionDate.equals(today)) {
            log.debug("Ежедневная задача уже выполнена сегодня");
            return;
        }

        log.info("Начало ежедневной задачи: {}", LocalTime.now());

        try {
            log.info("Шаг 1: подготовка дневных данных (копирование инструментов, расчёт цен)");
            boolean prepared = dataPreparationService.prepareDailyData();

            if (!prepared) {
                log.warn("Подготовка данных не завершилась успешно, заявки не будут выставлены");
                return;
            }

            log.info("Шаг 2: выставление BUY-заявок");
            ordersTask.run();
            log.info("BUY-заявки отправлены");

            lastExecutionDate = today;

            if (tableRefreshCallback != null) {
                tableRefreshCallback.run();
            }

            log.info("Ежедневная задача завершена успешно");
        } catch (Exception e) {
            log.error("Ошибка при выполнении ежедневной задачи", e);
        }
    }

    private boolean isTradeDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    private long calculateInitialDelay() {
        LocalTime now = LocalTime.now();
        LocalTime target = dailyExecutionTime;
        Duration duration = Duration.between(now, target);

        if (duration.isNegative()) {
            // Уже позже времени запуска — выполняем задачу сразу
            return 0;
        }
        if (duration.isZero()) {
            // Запустили ровно в start_time
            return 0;
        }
        // Ещё не дошли до времени запуска — ждём до него
        return duration.getSeconds();
    }


    public void setDailyExecutionTime(LocalTime time) {
        this.dailyExecutionTime = time;
        log.info("Время ежедневного выполнения установлено: {}", time);
    }

    public void setOrderCheckInterval(int seconds) {
        this.orderCheckIntervalSeconds = seconds;
        log.info("Интервал проверки заявок установлен: {} сек", seconds);
    }

    private String getActiveAccountId() {
        try {
            return AccountService.getActiveAccountId();
        } catch (Exception e) {
            log.error("Ошибка получения активного Account ID, используется заглушка", e);
            return "default_account";
        }
    }

    public void stop() {
        if (!isRunning) {
            log.warn("OrdersScheduler не запущен");
            return;
        }

        log.info("Остановка OrdersScheduler...");

        try {
            scheduler.shutdownNow();
            orderTracker.shutdown();

            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Scheduler не успел корректно остановиться");
            }

            isRunning = false;
            log.info("OrdersScheduler остановлен");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Ошибка остановки Scheduler'а", e);
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public String toString() {
        return String.format(
                "OrdersScheduler{status=%s, dailyTime=%s, orderCheckInterval=%ds}",
                isRunning ? "RUNNING" : "STOPPED",
                dailyExecutionTime,
                orderCheckIntervalSeconds
        );
    }
}
