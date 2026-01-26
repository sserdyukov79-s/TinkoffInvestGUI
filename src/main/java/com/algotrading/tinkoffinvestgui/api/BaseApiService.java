package com.algotrading.tinkoffinvestgui.api;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.util.concurrent.TimeUnit;

/**
 * Базовый класс для API сервисов Tinkoff Invest.
 * Управляет подключением к gRPC серверу.
 */
public class BaseApiService {
    protected String token;
    protected String apiUrl;
    protected int apiPort;
    protected ManagedChannel channel;

    public BaseApiService(String token, String apiUrl, int apiPort) {
        this.token = token;
        this.apiUrl = apiUrl;
        this.apiPort = apiPort;

        System.out.println("🔌 Инициализирую подключение к API:");
        System.out.println("   URL: " + apiUrl + ":" + apiPort);
        System.out.println("   Token: " + (token != null ? token.substring(0, Math.min(10, token.length())) + "..." : "NONE"));
    }

    /**
     * Получает или создает gRPC канал
     */
    protected ManagedChannel getChannel() {
        if (channel == null || channel.isShutdown()) {
            System.out.println("📡 Создаю новое gRPC соединение...");
            channel = NettyChannelBuilder
                    .forAddress(apiUrl, apiPort)
                    .useTransportSecurity()
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(5, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true)
                    .maxRetryAttempts(3)
                    .retryBufferSize(16 * 1024 * 1024)
                    .perRpcBufferLimit(1024 * 1024)
                    .build();
            System.out.println("✓ gRPC соединение установлено");
        }
        return channel;
    }

    /**
     * Получает метаданные авторизации с токеном
     */
    protected io.grpc.Metadata getAuthorizationHeaders() {
        io.grpc.Metadata headers = new io.grpc.Metadata();

        // Ключ для авторизации в Tinkoff API
        io.grpc.Metadata.Key<String> authKey =
                io.grpc.Metadata.Key.of("authorization", io.grpc.Metadata.ASCII_STRING_MARSHALLER);

        headers.put(authKey, "Bearer " + token);

        return headers;
    }

    /**
     * Валидирует токен перед использованием
     */
    protected void validateToken() {
        if (token == null || token.trim().isEmpty()) {
            throw new RuntimeException("❌ Токен пуст! Проверь invest.properties или БД");
        }
        if (!token.startsWith("t.")) {
            System.out.println("⚠️  Внимание: токен не начинается с 't.', может быть невалидным");
        }
        System.out.println("✓ Токен валиден (длина: " + token.length() + ")");
    }

    /**
     * Обработчик ошибок API
     */
    protected RuntimeException handleApiError(String context, Exception e) {
        String errorMsg = "Ошибка при " + context + ": " + e.getMessage();
        System.err.println("❌ " + errorMsg);

        if (e instanceof io.grpc.StatusRuntimeException) {
            io.grpc.StatusRuntimeException sre = (io.grpc.StatusRuntimeException) e;
            System.err.println("   Код ошибки: " + sre.getStatus().getCode());
            System.err.println("   Описание: " + sre.getStatus().getDescription());

            // Подсказки для типичных ошибок
            switch (sre.getStatus().getCode()) {
                case UNAVAILABLE:
                    System.err.println("   💡 Подсказка: Проверь:");
                    System.err.println("      - Доступность API: invest-public-api.tinkoff.ru:443");
                    System.err.println("      - Firewall/Прокси может блокировать подключение");
                    System.err.println("      - Проверь интернет соединение");
                    break;
                case UNAUTHENTICATED:
                    System.err.println("   💡 Подсказка: Токен невалидный или истек");
                    break;
                case PERMISSION_DENIED:
                    System.err.println("   💡 Подсказка: Токен не имеет прав на эту операцию");
                    break;
                default:
                    break;
            }
        }

        return new RuntimeException(errorMsg, e);
    }

    /**
     * Закрывает gRPC канал
     */
    public void close() {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
