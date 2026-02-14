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
        log.info("🔄 Синхронизация сделок с {} по {} для счёта {}", from, to, accountId);

        TinkoffApiService apiService = new TinkoffApiService(
                ConnectorConfig.getApiToken(),
                accountId
        );

        try {
            // Запрос сделок через Operations API
            Timestamp fromTs = timestampFromLocalDate(from);
            Timestamp toTs = timestampFromLocalDate(to.plusDays(1));

            log.info("📅 Период запроса: {} - {}",
                    Instant.ofEpochSecond(fromTs.getSeconds()),
                    Instant.ofEpochSecond(toTs.getSeconds()));

            OperationsRequest request = OperationsRequest.newBuilder()
                    .setAccountId(accountId)
                    .setFrom(fromTs)
                    .setTo(toTs)
                    .setState(OperationState.OPERATION_STATE_EXECUTED)
                    .build();

            OperationsServiceGrpc.OperationsServiceBlockingStub stub =
                    apiService.getOperationsStub();

            OperationsResponse response = stub.getOperations(request);

            log.info("📦 Получено операций от API: {}", response.getOperationsCount());

            int newTrades = 0;
            int updatedTrades = 0;
            int skippedTrades = 0;

            for (Operation operation : response.getOperationsList()) {
                log.debug("📋 Операция: type='{}', id='{}', figi='{}', quantity={}",
                        operation.getType(), operation.getId(), operation.getFigi(), operation.getQuantity());

                // Обрабатываем только сделки (не дивиденды, купоны и пр.)
                if (operation.getType().contains("Покупка") ||
                        operation.getType().contains("Продажа") ||
                        operation.getType().contains("Buy") ||
                        operation.getType().contains("Sell")) {

                    log.info("✅ Обрабатываем сделку: {} {} (ID: {})",
                            operation.getType(), operation.getFigi(), operation.getId());

                    Trade existingTrade = tradesRepository.findByTradeId(operation.getId());

                    Trade trade = createTradeFromOperation(operation, accountId);
                    tradesRepository.save(trade);

                    if (existingTrade == null) {
                        newTrades++;
                        log.info("➕ Новая сделка добавлена: {}", operation.getId());
                    } else {
                        updatedTrades++;
                        log.info("🔄 Сделка обновлена: {}", operation.getId());
                    }
                } else {
                    skippedTrades++;
                    log.debug("⏭️ Пропускаем операцию типа: {}", operation.getType());
                }
            }

            log.info("✅ Синхронизация завершена: новых={}, обновлено={}, пропущено={}",
                    newTrades, updatedTrades, skippedTrades);

        } catch (Exception e) {
            log.error("❌ Ошибка синхронизации сделок через API", e);
            throw new RuntimeException("Ошибка синхронизации сделок: " + e.getMessage(), e);
        } finally {
            apiService.close();
        }
    }


    /**
     * Создание объекта Trade из Operation API
     */

    private Trade createTradeFromOperation(Operation operation, String accountId) {
        Trade trade = new Trade();

        trade.setTradeId(operation.getId());
        trade.setAccountId(accountId);
        trade.setFigi(operation.getFigi());

        // Получаем доп. инфо об инструменте из БД (с защитой от ошибок)
        try {
            var instrument = instrumentsRepository.findByFigi(operation.getFigi());
            if (instrument != null) {
                trade.setInstrumentName(instrument.getName());
                trade.setTicker(instrument.getIsin());
            } else {
                log.warn("⚠️ Инструмент не найден в БД: {}", operation.getFigi());
                trade.setInstrumentName(operation.getFigi());
                trade.setTicker("");
            }
        } catch (Exception e) {
            log.error("❌ Ошибка получения инструмента {}: {}", operation.getFigi(), e.getMessage());
            trade.setInstrumentName(operation.getFigi());
            trade.setTicker("");
        }

        trade.setInstrumentType(operation.getInstrumentType());

        // Направление
        String direction = operation.getOperationType().name().contains("BUY") ||
                operation.getType().contains("Покупка") ? "BUY" : "SELL";
        trade.setDirection(direction);

        trade.setQuantity(Math.abs(operation.getQuantity()));
        trade.setPrice(MoneyConverter.toBigDecimal(operation.getPrice()));

        // Сумма сделки (payment)
        BigDecimal payment = MoneyConverter.toBigDecimal(operation.getPayment());
        trade.setTradeAmount(payment.abs());

        // ✅ КОМИССИЯ - вычисляем как разницу между payment и (price * quantity)
        // Для покупки: payment = -(price * quantity + commission)
        // Для продажи: payment = price * quantity - commission
        BigDecimal priceTotal = trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));
        BigDecimal commission = payment.abs().subtract(priceTotal).abs();

        // Если комиссия получилась слишком большой (>10% от суммы), значит ошибка в расчёте
        if (commission.compareTo(priceTotal.multiply(BigDecimal.valueOf(0.1))) > 0) {
            log.warn("⚠️ Комиссия подозрительно большая: {} (сумма сделки: {})", commission, priceTotal);
            commission = BigDecimal.ZERO;
        }

        trade.setCommission(commission);
        log.debug("💰 Расчётная комиссия: {} (payment={}, price*qty={})",
                commission, payment, priceTotal);

        // ✅ НКД и доходность - пока недоступны в данной версии API
        // Попробуем извлечь из OperationTrade если есть
        BigDecimal aci = BigDecimal.ZERO;
        if (operation.getTradesCount() > 0) {
            log.debug("📋 Операция содержит {} внутренних сделок", operation.getTradesCount());
            for (OperationTrade opTrade : operation.getTradesList()) {
                log.debug("  - Trade: datetime={}, quantity={}, price={}",
                        opTrade.getDateTime(), opTrade.getQuantity(), opTrade.getPrice());

                // НКД может быть в поле yield_relative или отдельно
                // Но в текущей protobuf схеме эти поля могут отсутствовать
                // Оставляем для будущих версий API
            }
        }

        trade.setAci(aci);
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
