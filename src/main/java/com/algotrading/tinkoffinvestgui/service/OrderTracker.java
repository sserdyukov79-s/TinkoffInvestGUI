package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.api.OrdersService;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.model.Order;
import com.algotrading.tinkoffinvestgui.repository.InstrumentsRepository;
import com.algotrading.tinkoffinvestgui.repository.OrdersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.MoneyValue;
import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.OrderState;
import ru.tinkoff.piapi.contract.v1.PostOrderResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * OrderTracker - отслеживает статусы заявок и выставляет Stop-on-Fill SELL-заявки.
 *
 * Логика:
 * 1. Периодически получает из БД PENDING/активные заявки.
 * 2. По каждой запрашивает актуальный статус у Tinkoff API.
 * 3. Если BUY-заявка заполнена (FILLED) — выставляет SELL с ценой из БД instruments.sell_price.
 * 4. Все изменения записываются в public.orders.
 */
public class OrderTracker {

    private static final Logger log = LoggerFactory.getLogger(OrderTracker.class);

    private final OrdersRepository ordersRepository;
    private final InstrumentsRepository instrumentsRepository;
    private final OrdersService ordersService;
    private final String accountId;

    public OrderTracker(OrdersRepository ordersRepository,
                        InstrumentsRepository instrumentsRepository,
                        String accountId) {
        this.ordersRepository = ordersRepository;
        this.instrumentsRepository = instrumentsRepository;
        this.accountId = accountId;
        this.ordersService = new OrdersService(
                ConnectorConfig.getApiToken(),
                ConnectorConfig.API_URL,
                ConnectorConfig.API_PORT
        );

        log.info("OrderTracker инициализирован для аккаунта: {}", accountId);
    }

    /**
     * Основной метод: проверяет статусы PENDING заявок.
     * Рекомендуется вызывать каждые N секунд из Scheduler'а.
     */
    public void checkAndProcessPendingOrders() {
        try {
            log.debug("Начало проверки статусов заявок...");

            List<Order> pendingOrders = ordersRepository.findByStatus("PENDING");
            if (pendingOrders.isEmpty()) {
                log.debug("Нет PENDING заявок для проверки");
                return;
            }

            log.info("Проверяем {} PENDING заявок...", pendingOrders.size());

            for (Order pendingOrder : pendingOrders) {
                try {
                    if (pendingOrder.getExchangeOrderId() == null) {
                        // Локальная заявка, которая ещё не ушла на биржу
                        continue;
                    }

                    OrderState apiOrder = ordersService.getOrderState(
                            accountId,
                            pendingOrder.getExchangeOrderId()
                    );

                    updateOrderFromAPI(pendingOrder, apiOrder);

                    if (isOrderFilled(apiOrder)) {
                        log.info("BUY заявка FILLED: {}", pendingOrder.getMyOrderId());
                        if (pendingOrder.getDirection() == OrderDirection.ORDER_DIRECTION_BUY) {
                            onBuyOrderFilled(pendingOrder);
                        }
                    }
                } catch (Exception e) {
                    log.error("Ошибка обработки заявки {}: {}", pendingOrder.getMyOrderId(), e.getMessage(), e);
                }
            }

            log.debug("Проверка статусов заявок завершена");
        } catch (Exception e) {
            log.error("Ошибка в checkAndProcessPendingOrders", e);
        }
    }

    private void updateOrderFromAPI(Order order, OrderState apiOrder) {
        order.setStatus(apiOrder.getExecutionReportStatus().name());
        order.setLotsExecuted(apiOrder.getLotsExecuted());

        if (apiOrder.hasExecutedOrderPrice()) {
            BigDecimal executedPrice = quotationToBigDecimal(apiOrder.getExecutedOrderPrice());
            order.setAverageExecutionPrice(executedPrice);
        }

        if (isOrderFilled(apiOrder) && order.getExecutedAt() == null) {
            order.setExecutedAt(Instant.now());
        }

        ordersRepository.update(order);

        log.info("Обновлена заявка {}: статус={}, выполнено={}/{}",
                order.getMyOrderId(),
                apiOrder.getExecutionReportStatus(),
                apiOrder.getLotsExecuted(),
                apiOrder.getLotsRequested());
    }

    private void onBuyOrderFilled(Order buyOrder) {
        try {
            log.info("BUY заявка заполнилась, выставляем связанную SELL (Stop-on-Fill)");
            log.info("BUY my_order_id={}, figi={}, lotsExecuted={}",
                    buyOrder.getMyOrderId(),
                    buyOrder.getFigi(),
                    buyOrder.getLotsExecuted());

            Instrument instrument = instrumentsRepository.findByFigi(buyOrder.getFigi());
            if (instrument == null) {
                log.error("Инструмент не найден для FIGI: {}", buyOrder.getFigi());
                return;
            }

            BigDecimal sellPrice = instrument.getSellPrice();
            if (sellPrice == null || sellPrice.signum() <= 0) {
                log.error("Некорректная цена продажи для {}, sellPrice={}",
                        instrument.getName(), sellPrice);
                return;
            }

            log.info("Цена продажи из БД: {}", sellPrice);

            Order sellOrder = createSellOrder(buyOrder, instrument, sellPrice);
            ordersRepository.save(sellOrder);
            log.info("SELL заявка сохранена в БД: {}", sellOrder.getMyOrderId());

            try {
                PostOrderResponse response = ordersService.postSellOrder(
                        accountId,
                        buyOrder.getFigi(),
                        (int) sellOrder.getLotsRequested(),
                        sellPrice
                );

                sellOrder.setExchangeOrderId(response.getOrderId());
                sellOrder.setStatus(response.getExecutionReportStatus().name());
                ordersRepository.update(sellOrder);

                log.info("SELL заявка выставлена на бирже: exchangeOrderId={}", response.getOrderId());

            } catch (Exception apiEx) {
                log.error("Ошибка выставления SELL заявки на бирже", apiEx);
                sellOrder.setStatus("ERROR");
                sellOrder.setErrorMessage("Ошибка API при выставлении SELL: " + apiEx.getMessage());
                ordersRepository.update(sellOrder);
            }
        } catch (Exception e) {
            log.error("Ошибка в onBuyOrderFilled", e);
        }
    }

    private Order createSellOrder(Order buyOrder, Instrument instrument, BigDecimal sellPrice) {
        Order sellOrder = new Order();
        sellOrder.setMyOrderId(UUID.randomUUID().toString());
        sellOrder.setAccountId(buyOrder.getAccountId());
        sellOrder.setFigi(buyOrder.getFigi());
        sellOrder.setTicker(instrument.getFigi());
        sellOrder.setInstrumentName(instrument.getName());
        sellOrder.setDirection(OrderDirection.ORDER_DIRECTION_SELL);
        sellOrder.setOrderType("LIMIT");

        long lots = buyOrder.getLotsExecuted() > 0 ? buyOrder.getLotsExecuted() : buyOrder.getLotsRequested();
        sellOrder.setLotsRequested(lots);
        sellOrder.setLotsExecuted(0L);
        sellOrder.setPrice(sellPrice);
        sellOrder.setStatus("PENDING");
        sellOrder.setParentOrderId(buyOrder.getMyOrderId());
        sellOrder.setParentFillTime(Instant.now());
        sellOrder.setCreatedAt(Instant.now());

        return sellOrder;
    }

    private boolean isOrderFilled(OrderState apiOrder) {
        return apiOrder.getLotsExecuted() >= apiOrder.getLotsRequested();
    }

    private BigDecimal quotationToBigDecimal(MoneyValue value) {
        long units = value.getUnits();
        int nano = value.getNano();
        return BigDecimal.valueOf(units).add(BigDecimal.valueOf(nano, 9));
    }

    public String getOrderStatus(String exchangeOrderId) {
        try {
            OrderState apiOrder = ordersService.getOrderState(accountId, exchangeOrderId);
            return apiOrder.getExecutionReportStatus().name();
        } catch (Exception e) {
            log.error("Ошибка получения статуса заявки от API", e);
            return "ERROR";
        }
    }

    public void cancelOrder(String exchangeOrderId) {
        try {
            ordersService.cancelOrder(accountId, exchangeOrderId);
            log.info("Заявка отменена: {}", exchangeOrderId);
        } catch (Exception e) {
            log.error("Ошибка отмены заявки", e);
            throw new RuntimeException("Не удалось отменить заявку", e);
        }
    }

    public void shutdown() {
        ordersService.shutdown();
    }
}