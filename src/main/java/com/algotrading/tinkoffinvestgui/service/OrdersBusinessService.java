package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.api.OrdersService;
import com.algotrading.tinkoffinvestgui.config.AppConstants;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.exception.DatabaseException;
import com.algotrading.tinkoffinvestgui.model.Order;
import com.algotrading.tinkoffinvestgui.repository.OrdersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.PostOrderResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrdersBusinessService {

    private static final Logger log = LoggerFactory.getLogger(OrdersBusinessService.class);

    private final OrdersRepository ordersRepository = new OrdersRepository();

    /**
     * Отправка заявок по списку инструментов.
     * AccountId берем из БД (parameters.account1).
     */
    public OrdersResult sendOrdersBatch(List<Instrument> instruments) {
        String accountId;
        try {
            accountId = AccountService.getActiveAccountId();
        } catch (DatabaseException e) {
            log.error("Ошибка получения account ID из БД", e);
            return new OrdersResult(
                    0,
                    instruments.size(),
                    "Ошибка получения Account ID из БД. " + e.getMessage()
            );
        }

        return sendOrdersBatch(instruments, accountId);
    }

    /**
     * Основная логика отправки + сохранение в public.orders.
     */
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

                    // Эффективные цены (manual приоритетнее auto)
                    BigDecimal effectiveBuyPrice = instrument.getEffectiveBuyPrice();
                    BigDecimal effectiveSellPrice = instrument.getEffectiveSellPrice();

                    // ===== ЗАЯВКА НА ПОКУПКУ =====
                    if (effectiveBuyPrice != null
                            && instrument.getBuyQuantity() != null
                            && instrument.getBuyQuantity() > 0) {

                        log.info("--- ЗАЯВКА НА ПОКУПКУ ---");
                        if (instrument.getManualBuyPrice() != null) {
                            log.info("Цена покупки (MANUAL): {}", effectiveBuyPrice);
                        } else {
                            log.info("Цена покупки (AUTO): {}", effectiveBuyPrice);
                        }
                        log.info("Количество: {}", instrument.getBuyQuantity());

                        PostOrderResponse buyResponse = ordersService.postBuyOrder(
                                accountId,
                                instrument.getFigi(),
                                instrument.getBuyQuantity(),
                                effectiveBuyPrice
                        );
                        successCount++;

                        // Сохранение BUY-заявки в public.orders
                        saveNewOrderToDb(
                                accountId,
                                instrument,
                                OrderDirection.ORDER_DIRECTION_BUY,
                                instrument.getBuyQuantity(),
                                effectiveBuyPrice,
                                buyResponse.getOrderId(),
                                null // без parentOrderId
                        );

                        Thread.sleep(AppConstants.ORDERSDELAYMILLIS);
                    }

                    // ===== ЗАЯВКА НА ПРОДАЖУ =====
                    if (effectiveSellPrice != null
                            && instrument.getSellQuantity() != null
                            && instrument.getSellQuantity() > 0) {

                        log.info("--- ЗАЯВКА НА ПРОДАЖУ ---");
                        if (instrument.getManualSellPrice() != null) {
                            log.info("Цена продажи (MANUAL): {}", effectiveSellPrice);
                        } else {
                            log.info("Цена продажи (AUTO): {}", effectiveSellPrice);
                        }
                        log.info("Количество: {}", instrument.getSellQuantity());

                        PostOrderResponse sellResponse = ordersService.postSellOrder(
                                accountId,
                                instrument.getFigi(),
                                instrument.getSellQuantity(),
                                effectiveSellPrice
                        );
                        successCount++;

                        // Сохранение SELL-заявки в public.orders
                        saveNewOrderToDb(
                                accountId,
                                instrument,
                                OrderDirection.ORDER_DIRECTION_SELL,
                                instrument.getSellQuantity(),
                                effectiveSellPrice,
                                sellResponse.getOrderId(),
                                null // при желании сюда можно передавать my_order_id покупки
                        );

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

    /**
     * Создает объект Order и сохраняет его в public.orders со статусом PENDING.
     */
    private void saveNewOrderToDb(String accountId,
                                  Instrument instrument,
                                  OrderDirection direction,
                                  int quantity,
                                  BigDecimal price,
                                  String exchangeOrderId,
                                  String parentOrderId) {
        try {
            Order order = new Order();

            // В качестве myOrderId используем тот же orderId, что вернул брокер
            order.setMyOrderId(exchangeOrderId);
            order.setExchangeOrderId(exchangeOrderId);

            order.setAccountId(accountId);
            order.setFigi(instrument.getFigi());
            // Если нет тикера, можно сохранить FIGI или оставить null
            order.setTicker(null);
            order.setInstrumentName(instrument.getName());

            order.setDirection(direction);
            order.setOrderType("LIMIT");

            order.setLotsRequested(quantity);
            order.setLotsExecuted(0L);

            order.setPrice(price);
            order.setAverageExecutionPrice(null);

            // Важно: PENDING, чтобы OrderTracker подобрал эту заявку
            order.setStatus("PENDING");

            order.setTotalOrderAmount(null);
            order.setCommission(null);
            order.setAci(null);

            order.setParentOrderId(parentOrderId);
            order.setParentFillTime(null);

            order.setErrorMessage(null);
            order.setCreatedAt(Instant.now());

            ordersRepository.save(order);
            log.info("Заявка сохранена в БД: {} ({})", order.getMyOrderId(), direction);
        } catch (Exception e) {
            // Не ломаем отправку всех заявок, просто логируем
            log.error("Не удалось сохранить заявку в БД по инструменту {}: {}",
                    instrument.getName(), e.getMessage(), e);
        }
    }

    // Эти методы сейчас нигде не используются, можно удалить или доработать под валидацию
    @SuppressWarnings("unused")
    private boolean isValidBuyOrder(Instrument instrument) {
        return instrument.getBuyPrice() != null
                && instrument.getBuyQuantity() != null
                && instrument.getBuyQuantity() > 0
                && instrument.getFigi() != null
                && !instrument.getFigi().isEmpty();
    }

    @SuppressWarnings("unused")
    private boolean isValidSellOrder(Instrument instrument) {
        return instrument.getSellPrice() != null
                && instrument.getSellQuantity() != null
                && instrument.getSellQuantity() > 0
                && instrument.getFigi() != null
                && !instrument.getFigi().isEmpty();
    }

    // ===== DTO результата =====

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
            return String.format("Успешно: %d, Ошибок: %d", successCount, errorCount);
        }
    }
}