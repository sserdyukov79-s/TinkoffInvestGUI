package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.exception.DatabaseException;
import com.algotrading.tinkoffinvestgui.model.Order;
import com.algotrading.tinkoffinvestgui.repository.InstrumentsRepository;
import com.algotrading.tinkoffinvestgui.repository.OrdersRepository;
import com.algotrading.tinkoffinvestgui.util.MoneyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.OrderState;

import java.time.Instant;
import java.util.List;

/**
 * Сервис, который при старте приложения подтягивает активные заявки из API
 * и синхронизирует их с таблицей public.orders.
 */
public class OrdersStartupService {

    private static final Logger log = LoggerFactory.getLogger(OrdersStartupService.class);

    private final OrdersRepository ordersRepository = new OrdersRepository();
    private final InstrumentsRepository instrumentsRepository = new InstrumentsRepository();

    /**
     * Основной метод синхронизации. Вызывается при старте GUI.
     */
    public void syncActiveOrdersOnStartup() {
        log.info("Синхронизация активных заявок с API при старте приложения...");

        String token = ConnectorConfig.getApiToken();
        String accountId;
        try {
            accountId = AccountService.getActiveAccountId();
        } catch (DatabaseException e) {
            log.error("Не удалось получить Account ID из БД для синхронизации заявок", e);
            return;
        }

        TinkoffApiService apiService = new TinkoffApiService(token, accountId);

        try {
            List<OrderState> apiOrders = apiService.getOrders();
            log.info("Из API получено {} активных заявок", apiOrders.size());

            int created = 0;
            int updated = 0;

            for (OrderState apiOrder : apiOrders) {
                String exchangeOrderId = apiOrder.getOrderId();

                Order existing = ordersRepository.findByExchangeOrderId(exchangeOrderId);

                if (existing == null) {
                    // Создаём новую запись
                    Order order = new Order();
                    order.setMyOrderId(exchangeOrderId);
                    order.setExchangeOrderId(exchangeOrderId);

                    order.setAccountId(accountId);
                    order.setFigi(apiOrder.getFigi());

                    Instrument instrument = instrumentsRepository.findByFigi(apiOrder.getFigi());
                    if (instrument != null) {
                        order.setInstrumentName(instrument.getName());
                        order.setTicker(instrument.getIsin());
                    } else {
                        order.setInstrumentName(null);
                        order.setTicker(null);
                    }

                    order.setDirection(apiOrder.getDirection());

                    // Нормализация order_type: из ORDER_TYPE_LIMIT -> LIMIT
                    String rawOrderType = apiOrder.getOrderType().name(); // e.g. ORDER_TYPE_LIMIT
                    order.setOrderType(rawOrderType.replace("ORDER_TYPE_", ""));

                    order.setLotsRequested(apiOrder.getLotsRequested());
                    order.setLotsExecuted(apiOrder.getLotsExecuted());

                    order.setInitialOrderPrice(
                            MoneyConverter.toBigDecimal(apiOrder.getInitialOrderPrice()));
                    order.setAverageExecutionPrice(
                            MoneyConverter.toBigDecimal(apiOrder.getExecutedOrderPrice()));

                    // Нормализация статуса: EXECUTION_REPORT_STATUS_NEW -> NEW
                    String rawStatus = apiOrder.getExecutionReportStatus().name();
                    order.setStatus(normalizeStatus(rawStatus));

                    order.setTotalOrderAmount(
                            MoneyConverter.toBigDecimal(apiOrder.getTotalOrderAmount()));
                    // commission / aci / errorMessage можно оставить null

                    // created_at — фиксация момента синхронизации
                    order.setCreatedAt(Instant.now());
                    order.setSubmittedAt(null); // точного времени выставления API не даёт здесь

                    ordersRepository.save(order);
                    created++;
                } else {
                    // Обновляем существующую запись
                    existing.setLotsExecuted(apiOrder.getLotsExecuted());
                    existing.setAverageExecutionPrice(
                            MoneyConverter.toBigDecimal(apiOrder.getExecutedOrderPrice()));
                    String rawStatus = apiOrder.getExecutionReportStatus().name();
                    existing.setStatus(normalizeStatus(rawStatus));
                    existing.setTotalOrderAmount(
                            MoneyConverter.toBigDecimal(apiOrder.getTotalOrderAmount()));

                    ordersRepository.update(existing);
                    updated++;
                }
            }

            log.info("Синхронизация заявок завершена: создано {}, обновлено {}", created, updated);
        } catch (Exception e) {
            log.error("Ошибка при синхронизации заявок с API", e);
        } finally {
            apiService.close();
        }
    }

    /**
     * Нормализация статуса из EXECUTION_REPORT_STATUS_* в короткий вид.
     */
    private String normalizeStatus(String rawStatus) {
        if (rawStatus == null) {
            return "UNKNOWN";
        }
        String s = rawStatus.replace("EXECUTION_REPORT_STATUS_", "");
        if ("PARTIALLYFILL".equals(s)) {
            return "PARTIALLY_FILLED";
        }
        return s;
    }
}
