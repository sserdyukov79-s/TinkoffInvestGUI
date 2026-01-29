package com.algotrading.tinkoffinvestgui.api;

import ru.tinkoff.piapi.contract.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

/**
 * Сервис для работы с облигациями Tinkoff Invest API.
 */
public class BondsService extends BaseApiService {

    public BondsService(String token, String apiUrl, int apiPort) {
        super(token, apiUrl, apiPort);
        validateToken();
    }

    /**
     * Получает список всех облигаций
     */
    public BondsResponse getBonds() {
        try {
            ManagedChannel channel = getChannel();
            Metadata headers = getAuthorizationHeaders();

            // Создаем stub для InstrumentsService
            InstrumentsServiceGrpc.InstrumentsServiceBlockingStub instrumentsService =
                    InstrumentsServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

            // Создаем запрос (INSTRUMENT_STATUS_BASE - только активные облигации)
            InstrumentsRequest request = InstrumentsRequest.newBuilder()
                    .setInstrumentStatus(InstrumentStatus.INSTRUMENT_STATUS_BASE)
                    .build();

            System.out.println("📡 Запрашиваю список облигаций...");
            BondsResponse response = instrumentsService.bonds(request);
            System.out.println("✓ Получено облигаций: " + response.getInstrumentsCount());

            return response;

        } catch (Exception e) {
            throw handleApiError("получении списка облигаций", e);
        }
    }

    /**
     * Форматирует номинал облигации
     */
    public static String formatNominal(MoneyValue nominal) {
        if (nominal == null) return "0";
        double value = nominal.getUnits() + nominal.getNano() / 1e9;
        return String.format("%.0f", value);
    }

    /**
     * Получает валюту из initialNominal
     */
    public static String getNominalCurrency(MoneyValue nominal) {
        if (nominal == null || nominal.getCurrency().isEmpty()) {
            return "--";
        }
        return nominal.getCurrency().toUpperCase();
    }

    /**
     * Форматирует dlong_client (nano)
     */
    public static String formatDlongClient(Quotation quotation) {
        if (quotation == null) return "0";
        double value = quotation.getUnits() + quotation.getNano() / 1e9;
        return String.format("%.2f", value);
    }

    /**
     * Форматирует дату погашения
     */
    public static String formatMaturityDate(com.google.protobuf.Timestamp timestamp) {
        if (timestamp == null) return "--";
        long seconds = timestamp.getSeconds();
        java.time.LocalDate date = java.time.LocalDateTime
                .ofEpochSecond(seconds, 0, java.time.ZoneOffset.UTC)
                .toLocalDate();
        return date.toString();
    }
}
