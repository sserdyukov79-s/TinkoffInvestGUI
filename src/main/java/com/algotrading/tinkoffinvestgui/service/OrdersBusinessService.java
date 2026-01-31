package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.api.OrdersService;
import com.algotrading.tinkoffinvestgui.config.AppConstants;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.exception.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Бизнес-логика для работы с заявками
 */
public class OrdersBusinessService {

    private static final Logger log = LoggerFactory.getLogger(OrdersBusinessService.class);

    /**
     * Отправляет массовые заявки для списка инструментов.
     * Account ID берётся автоматически из БД (parameters.account1)
     */
    public OrdersResult sendOrdersBatch(List<Instrument> instruments) {
        // Получаем account ID из БД
        String accountId;
        try {
            accountId = AccountService.getActiveAccountId();
        } catch (DatabaseException e) {
            log.error("❌ Не удалось получить account ID из БД", e);
            return new OrdersResult(0, instruments.size(),
                    "Account ID не настроен в БД. " + e.getMessage());
        }

        return sendOrdersBatch(instruments, accountId);
    }

    /**
     * Отправляет массовые заявки для списка инструментов с указанным accountId
     * (внутренний метод)
     */
    private OrdersResult sendOrdersBatch(List<Instrument> instruments, String accountId) {
        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║ МАССОВАЯ ОТПРАВКА ЗАЯВОК НА БИРЖУ");
        log.info("╠══════════════════════════════════════════════════════════════");
        log.info("║ Account ID (из БД): {}", accountId);
        log.info("║ Количество инструментов: {}", instruments.size());
        log.info("╚══════════════════════════════════════════════════════════════");

        OrdersService ordersService = new OrdersService(
                ConnectorConfig.getApiToken(),
                ConnectorConfig.API_URL,
                ConnectorConfig.API_PORT
        );

        int successCount = 0;
        int errorCount = 0;
        StringBuilder errors = new StringBuilder();

        try {
            for (Instrument instrument : instruments) {
                try {
                    log.info("\n📊 Обработка инструмента: {}", instrument.getName());
                    log.info("   FIGI: {}", instrument.getFigi());
                    log.info("   ISIN: {}", instrument.getIsin());
                    log.info("   Приоритет: {}", instrument.getPriority());

                    // Отправляем заявку на покупку (если указана)
                    if (isValidBuyOrder(instrument)) {
                        log.info("\n🟢 Отправка заявки на ПОКУПКУ:");
                        log.info("   Цена: {}", instrument.getBuyPrice());
                        log.info("   Количество: {}", instrument.getBuyQuantity());

                        ordersService.postBuyOrder(
                                accountId,
                                instrument.getFigi(),
                                instrument.getBuyQuantity(),
                                instrument.getBuyPrice()
                        );

                        successCount++;
                        Thread.sleep(AppConstants.ORDERS_DELAY_MILLIS);
                    }

                    // Отправляем заявку на продажу (если указана)
                    if (isValidSellOrder(instrument)) {
                        log.info("\n🔴 Отправка заявки на ПРОДАЖУ:");
                        log.info("   Цена: {}", instrument.getSellPrice());
                        log.info("   Количество: {}", instrument.getSellQuantity());

                        ordersService.postSellOrder(
                                accountId,
                                instrument.getFigi(),
                                instrument.getSellQuantity(),
                                instrument.getSellPrice()
                        );

                        successCount++;
                        Thread.sleep(AppConstants.ORDERS_DELAY_MILLIS);
                    }

                } catch (Exception e) {
                    log.error("❌ Ошибка отправки заявки для {}: {}",
                            instrument.getName(), e.getMessage(), e);
                    errorCount++;
                    errors.append(String.format("- %s: %s\n",
                            instrument.getName(), e.getMessage()));
                }
            }

        } finally {
            ordersService.shutdown();
        }

        log.info("\n╔══════════════════════════════════════════════════════════════");
        log.info("║ ИТОГИ ОТПРАВКИ ЗАЯВОК");
        log.info("╠══════════════════════════════════════════════════════════════");
        log.info("║ ✅ Успешно отправлено: {}", successCount);
        log.info("║ ❌ Ошибок: {}", errorCount);
        log.info("╚══════════════════════════════════════════════════════════════");

        return new OrdersResult(successCount, errorCount, errors.toString());
    }

    /**
     * Проверяет валидность заявки на покупку
     */
    private boolean isValidBuyOrder(Instrument instrument) {
        return instrument.getBuyPrice() != null &&
                instrument.getBuyQuantity() != null &&
                instrument.getBuyQuantity() > 0 &&
                instrument.getFigi() != null &&
                !instrument.getFigi().isEmpty();
    }

    /**
     * Проверяет валидность заявки на продажу
     */
    private boolean isValidSellOrder(Instrument instrument) {
        return instrument.getSellPrice() != null &&
                instrument.getSellQuantity() != null &&
                instrument.getSellQuantity() > 0 &&
                instrument.getFigi() != null &&
                !instrument.getFigi().isEmpty();
    }

    /**
     * Результат массовой отправки заявок
     */
    public static class OrdersResult {
        private final int successCount;
        private final int errorCount;
        private final String errors;

        public OrdersResult(int successCount, int errorCount, String errors) {
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.errors = errors;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public String getErrors() {
            return errors;
        }

        public boolean hasErrors() {
            return errorCount > 0;
        }

        public String getSummary() {
            return String.format("✅ Успешно: %d | ❌ Ошибок: %d", successCount, errorCount);
        }
    }
}
