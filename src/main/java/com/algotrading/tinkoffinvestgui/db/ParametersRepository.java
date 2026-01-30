package com.algotrading.tinkoffinvestgui.db;

import com.algotrading.tinkoffinvestgui.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Репозиторий для работы с таблицей parameters в PostgreSQL.
 * Позволяет читать и записывать параметры (токены, аккаунты и т.д.)
 */
public class ParametersRepository {

    // ✅ ДОБАВЛЯЕМ ЛОГГЕР
    private static final Logger log = LoggerFactory.getLogger(ParametersRepository.class);

    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;

    public ParametersRepository(DatabaseConfig config) {
        this.dbUrl = config.getDbUrl();
        this.dbUsername = config.getDbUsername();
        this.dbPassword = config.getDbPassword();

        // Загружаем драйвер PostgreSQL
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL драйвер не найден. Добавьте зависимость: " +
                    "implementation 'org.postgresql:postgresql:42.7.0'", e);
        }
    }

    /**
     * Получает значение параметра по ключу
     * Пример: getParameter("token1") → возвращает токен из БД
     */
    public String getParameter(String parameterName) {
        String query = "SELECT value FROM parameters WHERE \"parameter\" = ?";

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, parameterName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения параметра " + parameterName + ": " + e.getMessage(), e);
        }

        return null;
    }

    /**
     * Сохраняет или обновляет параметр
     * Использует INSERT ... ON CONFLICT для upsert операции
     */
    public void setParameter(String parameterName, String value) {
        String query = "INSERT INTO parameters (\"parameter\", value) VALUES (?, ?) " +
                "ON CONFLICT (\"parameter\") DO UPDATE SET value = EXCLUDED.value";

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, parameterName);
            stmt.setString(2, value);
            stmt.executeUpdate();
            log.info("Параметр {} сохранен в БД", parameterName);

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка сохранения параметра " + parameterName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Проверяет соединение с БД
     */
    public boolean testConnection() {
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
            return conn.isValid(2);
        } catch (SQLException e) {
            log.error("Ошибка подключения к БД: {}", e.getMessage());
            return false;
        }
    }
}
