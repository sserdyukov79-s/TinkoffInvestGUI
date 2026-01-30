package com.algotrading.tinkoffinvestgui.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Конфигурация для подключения к Tinkoff Invest API.
 * Параметры загружаются из invest.properties файла.
 */
public class ConnectorConfig {

    // ✅ ДОБАВЛЯЕМ ЛОГГЕР
    private static final Logger log = LoggerFactory.getLogger(ConnectorConfig.class);

    private static final Properties properties = new Properties();
    private static String cachedToken = null;

    // Статические переменные для кэширования
    public static final String API_URL;
    public static final int API_PORT;
    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASSWORD;

    static {
        try {
            // Загружаем файл invest.properties из classpath
            InputStream input = ConnectorConfig.class.getClassLoader()
                    .getResourceAsStream("invest.properties");

            if (input == null) {
                throw new RuntimeException("❌ Файл invest.properties не найден в src/main/resources/");
            }

            properties.load(input);
            input.close();
            log.info("✓ Файл invest.properties загружен успешно");
        } catch (IOException e) {
            throw new RuntimeException("❌ Ошибка при загрузке invest.properties: " + e.getMessage(), e);
        }

        // Парсим target: "invest-public-api.tinkoff.ru:443" → URL и PORT
        String target = getProperty("target", "invest-public-api.tinkoff.ru:443");
        String[] parts = target.split(":");
        API_URL = parts[0];
        API_PORT = parts.length > 1 ? Integer.parseInt(parts[1]) : 443;

        // Параметры БД
        DB_URL = getProperty("db.url", "jdbc:postgresql://localhost:5432/algotrade");
        DB_USER = getProperty("db.username", "trader");
        DB_PASSWORD = getProperty("db.password", "SecurePass123!");
    }

    /**
     * Получает свойство из invest.properties с значением по умолчанию
     */
    private static String getProperty(String key, String defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            log.info("⚠️ Свойство '{}' не найдено, используется значение по умолчанию: {}", key, defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * Получает API токен из БД PostgreSQL
     *
     * SELECT value FROM parameters WHERE parameter = 'token1'
     *
     * Если токен не найден в БД, пытается получить из invest.properties
     */
    public static String getApiToken() {
        // Если токен уже загружен, используй кэш
        if (cachedToken != null && !cachedToken.isEmpty()) {
            return cachedToken;
        }

        try {
            // Сначала пытаемся получить из БД
            cachedToken = getTokenFromDatabase();
            if (cachedToken != null && !cachedToken.isEmpty()) {
                log.info("✓ Токен загружен из БД (длина: {} символов)", cachedToken.length());
                return cachedToken;
            }

        } catch (Exception e) {
            log.info("⚠️ БД недоступна, пытаюсь получить токен из invest.properties");
        }

        // Если БД не работает, получаем из invest.properties
        String propertyToken = properties.getProperty("token");
        if (propertyToken != null && !propertyToken.trim().isEmpty()) {
            cachedToken = propertyToken;
            log.info("✓ Токен загружен из invest.properties (длина: {} символов)", cachedToken.length());
            return cachedToken;
        }

        throw new RuntimeException("❌ Не удалось получить токен ни из БД, ни из invest.properties!\n" +
                "Проверь:\n" +
                "1. Файл src/main/resources/invest.properties содержит: token=t.YOUR_TOKEN\n" +
                "2. БД PostgreSQL запущена и содержит таблицу parameters с токеном");
    }

    /**
     * Получает токен из БД PostgreSQL
     */
    private static String getTokenFromDatabase() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL драйвер не найден", e);
        }

        log.info("📡 Подключаюсь к БД: {}", DB_URL);
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            log.info("✓ Соединение с БД установлено");
            String query = "SELECT value FROM parameters WHERE parameter = 'token1'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                if (rs.next()) {
                    String token = rs.getString("value");
                    if (token != null && !token.trim().isEmpty()) {
                        return token;
                    }
                }
            }
        } catch (SQLException e) {
            log.info("⚠️ Ошибка подключения к БД: {}", e.getMessage());
            throw e;
        }

        return null;
    }

    /**
     * Очищает кэш токена
     */
    public static void clearTokenCache() {
        cachedToken = null;
    }

    /**
     * Возвращает информацию о конфигурации
     */
    public static void printConfig() {
        log.info("\n=== КОНФИГУРАЦИЯ ===");
        log.info("API URL: {}", API_URL);
        log.info("API PORT: {}", API_PORT);
        log.info("DB URL: {}", DB_URL);
        log.info("DB User: {}", DB_USER);
        log.info("Target: {}", properties.getProperty("target"));
        log.info("Sandbox: {}", properties.getProperty("sandbox.enabled"));
        log.info("====================\n");
    }

    /**
     * Получает произвольное свойство из invest.properties
     */
    public static String getPropertyValue(String key) {
        return properties.getProperty(key);
    }
}
