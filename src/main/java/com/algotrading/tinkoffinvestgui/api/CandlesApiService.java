package com.algotrading.tinkoffinvestgui.api;

import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Сервис для работы с историческими свечами через Tinkoff Invest API (gRPC)
 */
public class CandlesApiService {
    private static final Logger log = LoggerFactory.getLogger(CandlesApiService.class);

    private final String apiToken;
    private final String apiUrl;
    private final int apiPort;

    public CandlesApiService(String apiToken, String apiUrl, int apiPort) {
        this.apiToken = apiToken;
        this.apiUrl = apiUrl;
        this.apiPort = apiPort;
    }

    /**
     * Получает исторические свечи для инструмента
     *
     * @param figi     FIGI инструмента
     * @param from     Начало периода
     * @param to       Конец периода
     * @param interval Интервал свечей
     * @return Список свечей
     */
    public List<HistoricCandle> getCandles(String figi, LocalDate from, LocalDate to, CandleInterval interval) {
        log.info("Запрос свечей: FIGI={}, период={} - {}, интервал={}",
                figi, from, to, interval.name());

        ManagedChannel channel = null;
        try {
            // Создаём gRPC канал
            channel = ManagedChannelBuilder
                    .forAddress(apiUrl, apiPort)
                    .useTransportSecurity()
                    .build();

            // Создаём метаданные с токеном авторизации
            Metadata metadata = new Metadata();
            Metadata.Key<String> authKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
            Metadata.Key<String> appNameKey = Metadata.Key.of("x-app-name", Metadata.ASCII_STRING_MARSHALLER);

            metadata.put(authKey, "Bearer " + apiToken);
            metadata.put(appNameKey, "algotrading.tinkoffinvestgui");

            // Создаём stub с метаданными через interceptor
            MarketDataServiceGrpc.MarketDataServiceBlockingStub stub =
                    MarketDataServiceGrpc.newBlockingStub(channel);

            // Правильный способ добавления метаданных
            ClientInterceptor interceptor = MetadataUtils.newAttachHeadersInterceptor(metadata);
            stub = stub.withInterceptors(interceptor);

            // Конвертируем LocalDate в Timestamp
            Timestamp fromTimestamp = localDateToTimestamp(from);
            Timestamp toTimestamp = localDateToTimestamp(to);

            // Формируем запрос
            GetCandlesRequest request = GetCandlesRequest.newBuilder()
                    .setFigi(figi)
                    .setFrom(fromTimestamp)
                    .setTo(toTimestamp)
                    .setInterval(interval)
                    .build();

            // Логируем полный JSON запроса
            try {
                String requestJson = JsonFormat.printer()
                        .includingDefaultValueFields()
                        .print(request);
                log.debug("📤 Полный JSON запрос на свечи:\n{}", requestJson);
            } catch (Exception e) {
                log.warn("Не удалось сериализовать запрос в JSON для логирования", e);
            }

            log.debug("Отправка запроса свечей через gRPC...");

            // Выполняем запрос
            GetCandlesResponse response = stub.getCandles(request);

            log.info("✅ Получено свечей: {}", response.getCandlesCount());
            return response.getCandlesList();

        } catch (Exception e) {
            log.error("❌ Ошибка получения свечей для FIGI: {}", figi, e);
            throw new RuntimeException("Ошибка получения свечей: " + e.getMessage(), e);
        } finally {
            if (channel != null) {
                channel.shutdown();
            }
        }
    }

    /**
     * Конвертирует LocalDate в Protobuf Timestamp (начало дня UTC)
     */
    private Timestamp localDateToTimestamp(LocalDate date) {
        Instant instant = date.atStartOfDay(ZoneId.of("UTC")).toInstant();
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    /**
     * Вспомогательный метод: получить свечи с использованием токена из конфига
     */
    public static List<HistoricCandle> getCandlesFromConfig(
            String figi, LocalDate from, LocalDate to, CandleInterval interval) {
        CandlesApiService service = new CandlesApiService(
                ConnectorConfig.getApiToken(),
                ConnectorConfig.API_URL,
                ConnectorConfig.API_PORT
        );
        return service.getCandles(figi, from, to, interval);
    }
}
