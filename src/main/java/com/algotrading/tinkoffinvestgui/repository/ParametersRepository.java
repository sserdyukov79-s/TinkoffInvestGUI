package com.algotrading.tinkoffinvestgui.repository;

import com.algotrading.tinkoffinvestgui.config.DatabaseConfig;
import com.algotrading.tinkoffinvestgui.config.DatabaseConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Репозиторий для работы с таблицей parameters в PostgreSQL.
 * Позволяет читать и записывать параметры (account ID, токены и т.д.)
 */
public class ParametersRepository {
    private static final Logger log = LoggerFactory.getLogger(ParametersRepository.class);

    /**
     * Получает значение параметра по ключу
     * @param parameterName Имя параметра (например, "account1")
     * @return Значение параметра или null если не найден
     */
    public String getParameterValue(String parameterName) {
        String query = "SELECT value FROM parameters WHERE \"parameter\" = ?";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, parameterName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("value");
                    log.debug("Параметр '{}' получен из БД: {}", parameterName, value);
                    return value;
                } else {
                    log.warn("Параметр '{}' не найден в БД", parameterName);
                    return null;
                }
            }
            
        } catch (SQLException e) {
            log.error("Ошибка получения параметра '{}' из БД", parameterName, e);
            throw new RuntimeException("Не удалось получить параметр '" + parameterName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Сохраняет или обновляет параметр
     * Использует INSERT ... ON CONFLICT для upsert операции
     * @param parameterName Имя параметра
     * @param value Значение параметра
     */
    public void saveParameter(String parameterName, String value) {
        String query = "INSERT INTO parameters (\"parameter\", value) VALUES (?, ?) " +
                       "ON CONFLICT (\"parameter\") DO UPDATE SET value = EXCLUDED.value";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, parameterName);
            stmt.setString(2, value);
            stmt.executeUpdate();
            
            log.info("✅ Параметр '{}' сохранён в БД: {}", parameterName, value);
            
        } catch (SQLException e) {
            log.error("❌ Ошибка сохранения параметра '{}' в БД", parameterName, e);
            throw new RuntimeException("Не удалось сохранить параметр '" + parameterName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Проверяет существование параметра в БД
     * @param parameterName Имя параметра
     * @return true если параметр существует, false если нет
     */
    public boolean parameterExists(String parameterName) {
        String value = getParameterValue(parameterName);
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Удаляет параметр из БД
     * @param parameterName Имя параметра
     */
    public void deleteParameter(String parameterName) {
        String query = "DELETE FROM parameters WHERE \"parameter\" = ?";
        
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, parameterName);
            int deleted = stmt.executeUpdate();
            
            if (deleted > 0) {
                log.info("Параметр '{}' удалён из БД", parameterName);
            } else {
                log.warn("Параметр '{}' не найден для удаления", parameterName);
            }
            
        } catch (SQLException e) {
            log.error("Ошибка удаления параметра '{}' из БД", parameterName, e);
            throw new RuntimeException("Не удалось удалить параметр '" + parameterName + "': " + e.getMessage(), e);
        }
    }
}
