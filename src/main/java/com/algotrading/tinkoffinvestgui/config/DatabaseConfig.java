package com.algotrading.tinkoffinvestgui.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Конфигурация для подключения к PostgreSQL БД.
 * Читает параметры из invest.properties
 */
public class DatabaseConfig {
    private final Properties properties;

    public DatabaseConfig(String propertiesPath) {
        this.properties = new Properties();
        try (FileInputStream fis = new FileInputStream(propertiesPath)) {
            properties.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка чтения " + propertiesPath + ": " + e.getMessage(), e);
        }
    }

    public String getDbUrl() {
        return properties.getProperty("db.url", "jdbc:postgresql://localhost:5432/algotrade");
    }

    public String getDbUsername() {
        return properties.getProperty("db.username", "trader");
    }

    public String getDbPassword() {
        return properties.getProperty("db.password", "SecurePass123!");
    }

    public String getDbDriver() {
        return properties.getProperty("db.driver", "org.postgresql.Driver");
    }
}
