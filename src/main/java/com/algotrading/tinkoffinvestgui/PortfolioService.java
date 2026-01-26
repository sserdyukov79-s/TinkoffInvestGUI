package com.algotrading.tinkoffinvestgui;

import ru.tinkoff.piapi.contract.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

public class PortfolioService {
    private final String token;

    public PortfolioService(String token) {
        this.token = token;
    }

    /**
     * Получает портфель по ID счёта
     */
    public PortfolioResponse getPortfolio(String accountId) {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("invest-public-api.tinkoff.ru", 443)
                .useTransportSecurity()
                .build();
        try {
            Metadata headers = new Metadata();
            headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);

            OperationsServiceGrpc.OperationsServiceBlockingStub operationsService =
                    OperationsServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

            PortfolioRequest request = PortfolioRequest.newBuilder()
                    .setAccountId(accountId)
                    .build();

            return operationsService.getPortfolio(request);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка получения портфеля: " + e.getMessage(), e);
        } finally {
            channel.shutdown();
        }
    }

    /**
     * Форматирует количество позиции (units + nano)
     */
    public static String formatQuantity(Quotation quantity) {
        if (quantity == null) return "0";
        return String.format("%.4f", quantity.getUnits() + quantity.getNano() / 1e9);
    }

    /**
     * Форматирует цену в рубли
     */
    public static String formatPrice(MoneyValue price) {
        if (price == null) return "0 ₽";
        double rubles = price.getUnits() + price.getNano() / 1e9;
        return String.format("%.2f ₽", rubles);
    }

    /**
     * Получает FIGI инструмента
     */
    public static String getFigi(PortfolioPosition position) {
        if (position != null && !position.getFigi().isEmpty()) {
            return position.getFigi();
        }
        return "--";
    }

    /**
     * Получает Ticker инструмента
     */
    public static String getTicker(PortfolioPosition position) {
        if (position != null && !position.getTicker().isEmpty()) {
            return position.getTicker();
        }
        return "--";
    }

    /**
     * Получает тип инструмента
     */
    public static String getInstrumentType(PortfolioPosition position) {
        if (position != null && !position.getInstrumentType().isEmpty()) {
            String type = position.getInstrumentType();
            switch (type) {
                case "share": return "Акция";
                case "bond": return "Облигация";
                case "etf": return "ETF";
                case "currency": return "Валюта";
                case "futures": return "Фьючерс";
                case "option": return "Опцион";
                default: return type;
            }
        }
        return "--";
    }

    /**
     * Получает код площадки (classCode)
     */
    public static String getClassCode(PortfolioPosition position) {
        if (position != null && !position.getClassCode().isEmpty()) {
            return position.getClassCode();
        }
        return "--";
    }
}
