package com.algotrading.tinkoffinvestgui;

import com.algotrading.tinkoffinvestgui.api.AccountsService;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;

/**
 * Простой тестовый класс для проверки подключения к Tinkoff Invest API
 */
public class TinkoffAccounts {

    public static void main(String[] args) {
        System.out.println("🚀 TINKOFF INVEST API - TEST\n");

        try {
            // Загружаем конфигурацию
            ConnectorConfig.printConfig();

            // Получаем токен из БД или invest.properties
            String token = ConnectorConfig.getApiToken();
            System.out.println("✓ Токен успешно загружен\n");

            // Подключаемся к API
            AccountsService service = new AccountsService();
            int count = service.getAccountsCount();
            System.out.println("✓ Количество счетов: " + count + "\n");

            // Получаем и выводим список счетов
            var response = service.getAccounts();
            System.out.println("📋 Список счетов:\n");

            response.getAccountsList().forEach(account -> {
                System.out.println("  ├─ ID: " + account.getId());
                System.out.println("  ├─ Имя: " + account.getName());
                System.out.println("  ├─ Тип: " + account.getType());
                System.out.println("  └─ Статус: " + account.getStatus() + "\n");
            });

            System.out.println("✅ Подключение работает корректно!");

        } catch (Exception e) {
            System.err.println("❌ Ошибка подключения: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
