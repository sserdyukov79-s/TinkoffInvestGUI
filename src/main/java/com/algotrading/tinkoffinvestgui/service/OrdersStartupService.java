package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.exception.DatabaseException;
import com.algotrading.tinkoffinvestgui.model.Order;
import com.algotrading.tinkoffinvestgui.repository.OrdersRepository;
import com.algotrading.tinkoffinvestgui.util.MoneyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.OrderState;

import java.util.List;

/**
 * Сервис, который при старте приложения подтягивает активные заявки из API
 * и синхронизирует их с таблицей public.orders.
 */
public class OrdersStartupService {

    private static final Logger log = LoggerFactory.getLogger(OrdersStartupService.class);

    private final OrdersRepository ordersRepository = new OrdersRepository();

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
                    order.setTicker(null);          // при желании можно подтянуть
                    order.setInstrumentName(null);  // из InstrumentsService/Tinkoff API

                    order.setDirection(apiOrder.getDirection());
                    order.setOrderType(apiOrder.getOrderType().name());
                    order.setLotsRequested(apiOrder.getLotsRequested());
                    order.setLotsExecuted(apiOrder.getLotsExecuted());
                    order.setInitialOrderPrice(
                            MoneyConverter.toBigDecimal(apiOrder.getInitialOrderPrice()));
                    order.setAverageExecutionPrice(
                            MoneyConverter.toBigDecimal(apiOrder.getExecutedOrderPrice()));
                    order.setStatus(apiOrder.getExecutionReportStatus().name());
                    order.setTotalOrderAmount(
                            MoneyConverter.toBigDecimal(apiOrder.getTotalOrderAmount()));
                    // commission / aci / errorMessage можно оставить null

                    ordersRepository.save(order);
                    created++;
                } else {
                    // Обновляем существующую запись
                    existing.setLotsExecuted(apiOrder.getLotsExecuted());
                    existing.setAverageExecutionPrice(
                            MoneyConverter.toBigDecimal(apiOrder.getExecutedOrderPrice()));
                    existing.setStatus(apiOrder.getExecutionReportStatus().name());
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
}
