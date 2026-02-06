package com.algotrading.tinkoffinvestgui.db;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Класс для работы с подключением к базе данных
 * Настройки читаются из invest.properties
 */
public class DatabaseConnection {

    private static Properties properties = null;

    static {
        loadProperties();
    }

    /**
     * Загрузка настроек из invest.properties
     */
    private static void loadProperties() {
        properties = new Properties();
        try (InputStream input = DatabaseConnection.class.getClassLoader()
                .getResourceAsStream("invest.properties")) {

            if (input == null) {
                System.err.println("❌ Файл invest.properties не найден в resources");
                return;
            }

            properties.load(input);
            System.out.println("✅ Настройки загружены из invest.properties");

        } catch (IOException ex) {
            System.err.println("❌ Ошибка загрузки invest.properties: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Получить подключение к БД PostgreSQL
     * Использует настройки: db.url, db.username, db.password из invest.properties
     *
     * @return Connection объект
     * @throws SQLException если подключение не удалось
     */
    public static Connection getConnection() throws SQLException {
        if (properties == null || properties.isEmpty()) {
            loadProperties();
        }

        // Читаем настройки из вашего invest.properties
        String dbUrl = properties.getProperty("db.url", "jdbc:postgresql://localhost:5432/algotrade");
        String dbUser = properties.getProperty("db.username", "trader");
        String dbPassword = properties.getProperty("db.password", "");

        // Загружаем драйвер PostgreSQL (если нужно)
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("❌ PostgreSQL Driver не найден: " + e.getMessage());
        }

        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Получить значение свойства из invest.properties
     * @param key ключ свойства
     * @return значение свойства или null
     */
    public static String getProperty(String key) {
        if (properties == null || properties.isEmpty()) {
            loadProperties();
        }
        return properties.getProperty(key);
    }

    /**
     * Получить значение свойства с дефолтным значением
     * @param key ключ свойства
     * @param defaultValue значение по умолчанию
     * @return значение свойства или defaultValue
     */
    public static String getProperty(String key, String defaultValue) {
        if (properties == null || properties.isEmpty()) {
            loadProperties();
        }
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Получить числовое значение свойства
     * @param key ключ свойства
     * @param defaultValue значение по умолчанию
     * @return числовое значение или defaultValue
     */
    public static int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("❌ Ошибка парсинга числа для ключа " + key + ": " + value);
            return defaultValue;
        }
    }

    /**
     * Получить boolean значение свойства
     * @param key ключ свойства
     * @param defaultValue значение по умолчанию
     * @return boolean значение или defaultValue
     */
    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }
}