package com.algotrading.tinkoffinvestgui;

import com.algotrading.tinkoffinvestgui.api.AccountsService;
import com.algotrading.tinkoffinvestgui.api.GrpcChannelManager;

public class TinkoffAccounts {
    public static void main(String[] args) {
        System.out.println("TINKOFF INVEST");
        try {
            ConnectorConfig realConfig = new ConnectorConfig("invest.properties");
            ConnectorConfig sandboxConfig = new ConnectorConfig("sandbox.properties");

            if (realConfig.getToken() == null || realConfig.getToken().trim().isEmpty()) {
                System.err.println("Нет токена в invest.properties");
                return;
            }

            AccountsService realService = new AccountsService(
                    realConfig.getToken(),
                    realConfig.getApiUrl(),
                    realConfig.getApiPort()
            );
            System.out.println("Реальных счетов: " + realService.getAccountsCount());

            AccountsService sandboxService = new AccountsService(
                    sandboxConfig.getToken(),
                    sandboxConfig.getApiUrl(),
                    sandboxConfig.getApiPort()
            );
            System.out.println("Sandbox счетов: " + sandboxService.getAccountsCount());
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
        } finally {
            // Корректно закрываем все gRPC каналы
            GrpcChannelManager.getInstance().shutdown();
        }
    }
}
