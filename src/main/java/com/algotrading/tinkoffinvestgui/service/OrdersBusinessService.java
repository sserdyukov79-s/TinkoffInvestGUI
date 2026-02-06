package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.api.OrdersService;
import com.algotrading.tinkoffinvestgui.config.AppConstants;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.exception.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

public class OrdersBusinessService {
    private static final Logger log = LoggerFactory.getLogger(OrdersBusinessService.class);

    public OrdersResult sendOrdersBatch(List<Instrument> instruments) {
        String accountId;
        try {
            accountId = AccountService.getActiveAccountId();
        } catch (DatabaseException e) {
            log.error("Ошибка получения account ID из БД", e);
            return new OrdersResult(0, instruments.size(),
                    "Ошибка получения Account ID из БД. " + e.getMessage());
        }

        return sendOrdersBatch(instruments, accountId);
    }

    private OrdersResult sendOrdersBatch(List<Instrument> instruments, String accountId) {
        log.info("=========================================");
        log.info("НАЧАЛО ОТПРАВКИ ЗАЯВОК");
        log.info("=========================================");
        log.info("Account ID: {}", accountId);
        log.info("Количество инструментов: {}", instruments.size());
        log.info("=========================================");

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
                    log.info("Обработка инструмента: {}", instrument.getName());
                    log.info("FIGI: {}", instrument.getFigi());
                    log.info("ISIN: {}", instrument.getIsin());
                    log.info("Приоритет: {}", instrument.getPriority());

                    // ✅ ИСПОЛЬЗУЕМ ЭФФЕКТИВНЫЕ ЦЕНЫ
                    BigDecimal effectiveBuyPrice = instrument.getEffectiveBuyPrice();
                    BigDecimal effectiveSellPrice = instrument.getEffectiveSellPrice();

                    // Заявка на покупку
                    if (effectiveBuyPrice != null
                            && instrument.getBuyQuantity() != null
                            && instrument.getBuyQuantity() > 0) {

                        log.info("--- ЗАЯВКА НА ПОКУПКУ ---");

                        // ✅ ЛОГИРУЕМ, ОТКУДА ВЗЯТА ЦЕНА
                        if (instrument.getManualBuyPrice() != null) {
                            log.info("Цена покупки (MANUAL): {}", effectiveBuyPrice);
                        } else {
                            log.info("Цена покупки (AUTO): {}", effectiveBuyPrice);
                        }

                        log.info("Количество: {}", instrument.getBuyQuantity());

                        ordersService.postBuyOrder(
                                accountId,
                                instrument.getFigi(),
                                instrument.getBuyQuantity(),
                                effectiveBuyPrice
                        );

                        successCount++;
                        Thread.sleep(AppConstants.ORDERSDELAYMILLIS);
                    }

                    // Заявка на продажу
                    if (effectiveSellPrice != null
                            && instrument.getSellQuantity() != null
                            && instrument.getSellQuantity() > 0) {

                        log.info("--- ЗАЯВКА НА ПРОДАЖУ ---");

                        // ✅ ЛОГИРУЕМ, ОТКУДА ВЗЯТА ЦЕНА
                        if (instrument.getManualSellPrice() != null) {
                            log.info("Цена продажи (MANUAL): {}", effectiveSellPrice);
                        } else {
                            log.info("Цена продажи (AUTO): {}", effectiveSellPrice);
                        }

                        log.info("Количество: {}", instrument.getSellQuantity());

                        ordersService.postSellOrder(
                                accountId,
                                instrument.getFigi(),
                                instrument.getSellQuantity(),
                                effectiveSellPrice
                        );

                        successCount++;
                        Thread.sleep(AppConstants.ORDERSDELAYMILLIS);
                    }

                } catch (Exception e) {
                    log.error("Ошибка по инструменту {}: {}",
                            instrument.getName(), e.getMessage(), e);
                    errorCount++;
                    errors.append(String.format("- %s: %s%n",
                            instrument.getName(), e.getMessage()));
                }
            }
        } finally {
            ordersService.shutdown();
            log.info("=========================================");
            log.info("ЗАВЕРШЕНИЕ ОТПРАВКИ ЗАЯВОК");
            log.info("=========================================");
            log.info("Успешных заявок: {}", successCount);
            log.info("Ошибок: {}", errorCount);
            log.info("=========================================");
        }

        return new OrdersResult(successCount, errorCount, errors.toString());
    }

    private boolean isValidBuyOrder(Instrument instrument) {
        return instrument.getBuyPrice() != null
                && instrument.getBuyQuantity() != null
                && instrument.getBuyQuantity() > 0
                && instrument.getFigi() != null
                && !instrument.getFigi().isEmpty();
    }

    private boolean isValidSellOrder(Instrument instrument) {
        return instrument.getSellPrice() != null
                && instrument.getSellQuantity() != null
                && instrument.getSellQuantity() > 0
                && instrument.getFigi() != null
                && !instrument.getFigi().isEmpty();
    }

    public static class OrdersResult {
        private final int successCount;
        private final int errorCount;
        private final String errors;

        public OrdersResult(int successCount, int errorCount, String errors) {
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.errors = errors;
        }

        public int getSuccessCount() { return successCount; }
        public int getErrorCount() { return errorCount; }
        public String getErrors() { return errors; }
        public boolean hasErrors() { return errorCount > 0; }

        public String getSummary() {
            return String.format("Успешно: %d, Ошибок: %d", successCount, errorCount);
        }
    }
}
