package com.algotrading.tinkoffinvestgui.api;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Управляет переиспользуемыми gRPC каналами.
 * Кеширует каналы по ключу (url:port) для оптимизации производительности.
 */
public class GrpcChannelManager {
    private static final GrpcChannelManager INSTANCE = new GrpcChannelManager();
    private final Map<String, ManagedChannel> channels = new HashMap<>();

    private GrpcChannelManager() {}

    public static GrpcChannelManager getInstance() {
        return INSTANCE;
    }

    /**
     * Получает или создает канал для указанного хоста и порта
     */
    public synchronized ManagedChannel getChannel(String host, int port) {
        String key = host + ":" + port;

        if (channels.containsKey(key)) {
            ManagedChannel channel = channels.get(key);
            if (!channel.isShutdown()) {
                return channel;
            }
            // Если канал закрыт, удалить и создать новый
            channels.remove(key);
        }

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .useTransportSecurity()
                .build();

        channels.put(key, channel);
        return channel;
    }

    /**
     * Закрывает все каналы при завершении приложения
     */
    public synchronized void shutdown() {
        for (String key : channels.keySet()) {
            ManagedChannel channel = channels.get(key);
            try {
                if (!channel.isShutdown()) {
                    channel.shutdown();
                    if (!channel.awaitTermination(10, TimeUnit.SECONDS)) {
                        channel.shutdownNow();
                    }
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        channels.clear();
    }
}
