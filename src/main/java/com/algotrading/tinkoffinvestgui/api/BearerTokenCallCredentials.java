package com.algotrading.tinkoffinvestgui.api;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;

import java.util.concurrent.Executor;

/**
 * Класс для авторизации gRPC запросов с помощью Bearer токена
 */
public class BearerTokenCallCredentials extends CallCredentials {

    private static final String AUTHORIZATION_METADATA_KEY = "Authorization";
    private static final String BEARER_TYPE = "Bearer";

    private final String token;

    public BearerTokenCallCredentials(String token) {
        this.token = token;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier applier) {
        executor.execute(() -> {
            try {
                Metadata headers = new Metadata();
                Metadata.Key<String> authorizationKey =
                        Metadata.Key.of(AUTHORIZATION_METADATA_KEY, Metadata.ASCII_STRING_MARSHALLER);

                headers.put(authorizationKey, String.format("%s %s", BEARER_TYPE, token));
                applier.apply(headers);

            } catch (Throwable e) {
                applier.fail(Status.UNAUTHENTICATED.withCause(e));
            }
        });
    }

    @Override
    public void thisUsesUnstableApi() {
        // Пустая реализация - требуется интерфейсом
    }
}
