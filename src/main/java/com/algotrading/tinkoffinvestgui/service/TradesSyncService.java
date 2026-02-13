package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.model.Trade;
import com.algotrading.tinkoffinvestgui.repository.InstrumentsRepository;
import com.algotrading.tinkoffinvestgui.repository.TradesRepository;
import com.algotrading.tinkoffinvestgui.util.MoneyConverter;
import com.google.protobuf.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Сервис синхронизации сделок с Tinkoff API
 */
public class TradesSyncService {

    private static final Logger log = LoggerFactory.getLogger(TradesSyncService.class);

    private final TradesRepository tradesRepository = new TradesRepository();
    private final InstrumentsRepository instrumentsRepository = new InstrumentsRepository();

    /**
     * Синхронизация сделок за сегодня
     */
    public void syncTodayTrades() {
        try {
            String accountId = AccountService.getActiveAccountId();
            syncTradesForPeriod(accountId, LocalDate.now(), LocalDate.now());
        } catch (Exception e) {
            log.error("Ошибка синхронизации сделок за сегодня", e);
        }
    }

    /**
     * Синхронизация сделок за период
     */
    public void syncTradesForPeriod(String accountId, LocalDate from, LocalDate to) {
        log.info("🔄 Синхронизация сделок с {} по {}", from, to);

        TinkoffApiService apiService = new TinkoffApiService(
                ConnectorConfig.getApiToken(),
                accountId
        );

        try {
            // Запрос сделок через Operations API
            OperationsRequest request = OperationsRequest.newBuilder()
                    .setAccountId(accountId)
                    .setFrom(timestampFromLocalDate(from))
                    .setTo(timestampFromLocalDate(to.plusDays(1)))  // +1 день чтобы включить конец периода
                    .setState(OperationState.OPERATION_STATE_EXECUTED)
                    .build();

            OperationsServiceGrpc.OperationsServiceBlockingStub stub =
                    apiService.getOperationsStub();

            OperationsResponse response = stub.getOperations(request);

            int newTrades = 0;
            int updatedTrades = 0;

            for (Operation operation : response.getOperationsList()) {
                // Обрабатываем только сделки (не дивиденды, купоны и пр.)
                if (operation.getType().equals("Покупка") ||
                        operation.getType().equals("Продажа") ||
                        operation.getType().equals("Покупка ЦБ") ||
                        operation.getType().equals("Продажа ЦБ")) {

                    Trade existingTrade = tradesRepository.findByTradeId(operation.getId());

                    Trade trade = createTradeFromOperation(operation, accountId);
                    tradesRepository.save(trade);

                    if (existingTrade == null) {
                        newTrades++;
                    } else {
                        updatedTrades++;
                    }
                }
            }

            log.info("✅ Синхронизация завершена: новых={}, обновлено={}", newTrades, updatedTrades);

        } catch (Exception e) {
            log.error("Ошибка синхронизации сделок через API", e);
        } finally {
            apiService.close();
        }
    }

    /**
     * Создание объекта Trade из Operation API
     */
    /**
     * Создание объекта Trade из Operation API
     */
    private Trade createTradeFromOperation(Operation operation, String accountId) {
        Trade trade = new Trade();

        trade.setTradeId(operation.getId());
        trade.setAccountId(accountId);
        trade.setFigi(operation.getFigi());

        // Получаем доп. инфо об инструменте из БД
        var instrument = instrumentsRepository.findByFigi(operation.getFigi());
        if (instrument != null) {
            trade.setInstrumentName(instrument.getName());
            trade.setTicker(instrument.getIsin());
        }

        trade.setInstrumentType(operation.getInstrumentType());

        // Направление
        String direction = operation.getOperationType().name().contains("BUY") ||
                operation.getType().contains("Покупка") ? "BUY" : "SELL";
        trade.setDirection(direction);

        trade.setQuantity(Math.abs(operation.getQuantity()));
        trade.setPrice(MoneyConverter.toBigDecimal(operation.getPrice()));

        // Сумма сделки (может быть отрицательной для покупок)
        BigDecimal payment = MoneyConverter.toBigDecimal(operation.getPayment());
        trade.setTradeAmount(payment.abs());

        // Комиссия - вычисляем из trades списка, если есть
        BigDecimal commission = BigDecimal.ZERO;
        for (OperationTrade opTrade : operation.getTradesList()) {
            // Комиссия не всегда есть в структуре, используем 0
            commission = commission.add(BigDecimal.ZERO);
        }
        trade.setCommission(commission);

        // НКД и доходность - для будущих версий API
        trade.setAci(BigDecimal.ZERO);
        trade.setYieldValue(BigDecimal.ZERO);

        // Дата сделки
        trade.setTradeDate(timestampToInstant(operation.getDate()));

        trade.setCurrency(operation.getCurrency());

        return trade;
    }


    private Timestamp timestampFromLocalDate(LocalDate date) {
        Instant instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private Instant timestampToInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
