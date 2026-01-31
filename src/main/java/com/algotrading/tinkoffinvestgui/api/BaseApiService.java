package com.algotrading.tinkoffinvestgui.api;

import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Базовый класс для API сервисов Tinkoff Invest.
 * Управляет подключением к gRPC серверу и авторизацией.
 */
public abstract class BaseApiService {
    protected static final Logger log = LoggerFactory.getLogger(BaseApiService.class);
    
    protected String token;
    protected String apiUrl;
    protected int apiPort;
    protected ManagedChannel channel;

    public BaseApiService(String token, String apiUrl, int apiPort) {
        this.token = token;
        this.apiUrl = apiUrl;
        this.apiPort = apiPort;
        
        log.debug("Инициализация BaseApiService: {}:{}", apiUrl, apiPort);
    }

    /**
     * Получает или создает gRPC канал
     */
    protected ManagedChannel getChannel() {
        if (channel == null || channel.isShutdown()) {
            log.debug("Создание нового gRPC соединения...");
            
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
            
            log.debug("gRPC соединение установлено");
        }
        
        return channel;
    }

    /**
     * Получает CallCredentials с Bearer токеном для авторизации
     */
    protected CallCredentials getCallCredentials() {
        return new BearerTokenCallCredentials(token);
    }

    /**
     * Получает метаданные авторизации с токеном (для старых сервисов)
     */
    protected Metadata getAuthorizationHeaders() {
        Metadata headers = new Metadata();
        Metadata.Key<String> authKey = 
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        headers.put(authKey, "Bearer " + token);
        return headers;
    }

    /**
     * Валидирует токен перед использованием (для старых сервисов)
     */
    protected void validateToken() {
        if (token == null || token.trim().isEmpty()) {
            throw new RuntimeException("❌ Токен пуст! Проверьте invest.properties или БД");
        }
        
        if (!token.startsWith("t.")) {
            log.warn("⚠️ Внимание: токен не начинается с 't.', может быть невалидным");
        }
        
        log.debug("✓ Токен валиден (длина: {})", token.length());
    }

    /**
     * Обработчик ошибок API (для старых сервисов)
     */
    protected RuntimeException handleApiError(String context, Exception e) {
        String errorMsg = "Ошибка при " + context + ": " + e.getMessage();
        log.error("❌ {}", errorMsg);
        
        if (e instanceof io.grpc.StatusRuntimeException) {
            io.grpc.StatusRuntimeException sre = (io.grpc.StatusRuntimeException) e;
            log.error(" Код ошибки: {}", sre.getStatus().getCode());
            log.error(" Описание: {}", sre.getStatus().getDescription());
            
            switch (sre.getStatus().getCode()) {
                case UNAVAILABLE:
                    log.error(" 💡 Подсказка: Проверьте доступность API и интернет-соединение");
                    break;
                case UNAUTHENTICATED:
                    log.error(" 💡 Подсказка: Токен невалидный или истек");
                    break;
                case PERMISSION_DENIED:
                    log.error(" 💡 Подсказка: Токен не имеет прав на эту операцию");
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
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            try {
                log.debug("Закрытие gRPC соединения...");
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                log.debug("gRPC соединение закрыто");
            } catch (InterruptedException e) {
                log.warn("Прерывание при закрытии канала");
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Альтернативное имя для shutdown() (для совместимости)
     */
    public void close() {
        shutdown();
    }
}
