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
        String url = properties.getProperty("db.url");
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalStateException(
                    "❌ Свойство 'db.url' не найдено в invest.properties!\n" +
                            "Добавьте в файл: db.url=jdbc:postgresql://localhost:5432/algotrade"
            );
        }
        return url;
    }

    public String getDbUsername() {
        String username = properties.getProperty("db.username");
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException(
                    "❌ Свойство 'db.username' не найдено в invest.properties!\n" +
                            "Добавьте в файл: db.username=your_username"
            );
        }
        return username;
    }

    public String getDbPassword() {
        String password = properties.getProperty("db.password");
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalStateException(
                    "❌ Свойство 'db.password' не найдено в invest.properties!\n" +
                            "Добавьте в файл: db.password=your_password"
            );
        }
        return password;
    }

    public String getDbDriver() {
        // Драйвер можно оставить с дефолтом - это не секрет
        return properties.getProperty("db.driver", "org.postgresql.Driver");
    }
}
