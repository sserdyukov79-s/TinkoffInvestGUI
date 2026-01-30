package com.algotrading.tinkoffinvestgui;

import com.algotrading.tinkoffinvestgui.api.AccountsService;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Простой тестовый класс для проверки подключения к Tinkoff Invest API
 */
public class TinkoffAccounts {

    // ✅ ДОБАВЛЯЕМ ЛОГГЕР
    private static final Logger log = LoggerFactory.getLogger(TinkoffAccounts.class);

    public static void main(String[] args) {
        log.info("🚀 TINKOFF INVEST API - TEST\n");

        try {
            // Загружаем конфигурацию
            ConnectorConfig.printConfig();

            // Получаем токен из БД или invest.properties
            String token = ConnectorConfig.getApiToken();
            log.info("✓ Токен успешно загружен\n");

            // Подключаемся к API
            AccountsService service = new AccountsService();
            int count = service.getAccountsCount();
            log.info("✓ Количество счетов: {}\n", count);

            // Получаем и выводим список счетов
            var response = service.getAccounts();
            log.info("📋 Список счетов:\n");

            response.getAccountsList().forEach(account -> {
                log.info(" ├─ ID: {}", account.getId());
                log.info(" ├─ Имя: {}", account.getName());
                log.info(" ├─ Тип: {}", account.getType());
                log.info(" └─ Статус: {}\n", account.getStatus());
            });

            log.info("✅ Подключение работает корректно!");

        } catch (Exception e) {
            log.error("❌ Ошибка подключения: {}", e.getMessage(), e);
        }
    }
}
