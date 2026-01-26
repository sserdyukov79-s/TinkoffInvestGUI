package com.algotrading.tinkoffinvestgui.api;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

/**
 * Базовый класс для всех API сервисов.
 * Содержит общую логику подключения и обработки ошибок.
 */
public abstract class BaseApiService {
    protected final String token;
    protected final String apiUrl;
    protected final int apiPort;

    public BaseApiService(String token, String apiUrl, int apiPort) {
        this.token = token;
        this.apiUrl = apiUrl;
        this.apiPort = apiPort;
    }

    /**
     * Получает переиспользуемый gRPC канал
     */
    protected ManagedChannel getChannel() {
        return GrpcChannelManager.getInstance().getChannel(apiUrl, apiPort);
    }

    /**
     * Создает заголовки с токеном авторизации
     */
    protected Metadata getAuthorizationHeaders() {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                "Bearer " + token);
        return headers;
    }

    /**
     * Проверяет, что токен не пустой
     */
    protected void validateToken() {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Токен авторизации не установлен");
        }
    }

    /**
     * Обрабатывает ошибки API
     */
    protected RuntimeException handleApiError(String operation, Exception e) {
        return new RuntimeException(
                String.format("Ошибка при %s: %s", operation, e.getMessage()),
                e);
    }
}
