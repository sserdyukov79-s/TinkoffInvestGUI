package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.repository.ParametersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Сервис для работы с Account ID из базы данных
 */
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final String ACCOUNT_PARAMETER = "account1";

    /**
     * Получает активный account ID из БД
     * @return Account ID из параметров БД
     * @throws RuntimeException если account ID не настроен
     */
    public static String getActiveAccountId() {
        log.info("📊 Получение активного account ID из БД (parameter: {})", ACCOUNT_PARAMETER);
        
        try {
            ParametersRepository repository = new ParametersRepository();
            String accountId = repository.getParameterValue(ACCOUNT_PARAMETER);
            
            if (accountId == null || accountId.trim().isEmpty()) {
                String errorMsg = String.format(
                    "Account ID не найден в БД. " +
                    "Добавьте запись: INSERT INTO parameters (\"parameter\", value) VALUES ('%s', 'your_account_id');",
                    ACCOUNT_PARAMETER
                );
                log.error("❌ {}", errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
            log.info("✅ Account ID получен из БД: {}", accountId);
            return accountId;
            
        } catch (Exception e) {
            log.error("❌ Ошибка получения account ID из БД", e);
            throw new RuntimeException("Не удалось получить account ID из БД: " + e.getMessage(), e);
        }
    }

    /**
     * Проверяет, настроен ли account ID в БД
     * @return true если настроен, false если нет
     */
    public static boolean isAccountConfigured() {
        try {
            String accountId = getActiveAccountId();
            return accountId != null && !accountId.trim().isEmpty();
        } catch (Exception e) {
            log.debug("Account ID не настроен: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Сохраняет account ID в БД
     * @param accountId Account ID для сохранения
     */
    public static void saveAccountId(String accountId) {
        log.info("💾 Сохранение account ID в БД: {}", accountId);
        
        try {
            ParametersRepository repository = new ParametersRepository();
            repository.saveParameter(ACCOUNT_PARAMETER, accountId);
            log.info("✅ Account ID сохранён успешно");
        } catch (Exception e) {
            log.error("❌ Ошибка сохранения account ID", e);
            throw new RuntimeException("Не удалось сохранить account ID: " + e.getMessage(), e);
        }
    }
}
