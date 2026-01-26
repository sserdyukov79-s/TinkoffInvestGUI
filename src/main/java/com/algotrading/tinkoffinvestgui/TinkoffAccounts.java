package com.algotrading.tinkoffinvestgui;

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

            AccountsService realService = new AccountsService(realConfig.getToken());
            System.out.println("Реальных счетов: " + realService.getAccountsCount());

            AccountsService sandboxService = new AccountsService(sandboxConfig.getToken());
            System.out.println("Sandbox счетов: " + sandboxService.getAccountsCount());
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }
}
