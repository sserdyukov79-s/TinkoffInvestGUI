package com.algotrading.tinkoffinvestgui.repository;

import com.algotrading.tinkoffinvestgui.config.DatabaseConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;

/**
 * Репозиторий для работы с таблицей parameters (конфигурационные параметры приложения)
 */
public class ParametersRepository {
    private static final Logger log = LoggerFactory.getLogger(ParametersRepository.class);

    /**
     * Получить значение параметра из таблицы parameters
     *
     * @param parameterName Название параметра (например, "start_time", "account1")
     * @return Значение параметра или null, если параметр не найден
     * @throws SQLException Если произошла ошибка при работе с БД
     */
    public String getParameterValue(String parameterName) throws SQLException {
        String sql = "SELECT value FROM parameters WHERE parameter = ?";
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, parameterName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("value");
                    log.debug("✅ Получен параметр '{}': '{}'", parameterName, value);
                    return value;
                } else {
                    log.warn("⚠️ Параметр '{}' не найден в таблице parameters", parameterName);
                    return null;
                }
            }
        } catch (SQLException e) {
            log.error("❌ Ошибка при получении параметра '{}' из БД", parameterName, e);
            throw e;
        }
    }

    /**
     * Установить или обновить значение параметра
     *
     * @param parameterName Название параметра
     * @param value Значение параметра
     * @throws SQLException Если произошла ошибка при работе с БД
     */
    public void setParameterValue(String parameterName, String value) throws SQLException {
        String sql = """
            INSERT INTO parameters (parameter, value)
            VALUES (?, ?)
            ON CONFLICT (parameter)
            DO UPDATE SET value = EXCLUDED.value
            """;
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, parameterName);
            stmt.setString(2, value);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                log.info("✅ Параметр '{}' установлен в значение '{}'", parameterName, value);
            } else {
                log.warn("⚠️ Не удалось установить параметр '{}'", parameterName);
            }
        } catch (SQLException e) {
            log.error("❌ Ошибка при установке параметра '{}' в значение '{}'",
                    parameterName, value, e);
            throw e;
        }
    }

    /**
     * Проверить, существует ли параметр в БД
     *
     * @param parameterName Название параметра
     * @return true если параметр существует, false иначе
     */
    public boolean parameterExists(String parameterName) {
        try {
            String value = getParameterValue(parameterName);
            return value != null;
        } catch (SQLException e) {
            log.error("❌ Ошибка при проверке существования параметра '{}'", parameterName, e);
            return false;
        }
    }

    /**
     * Удалить параметр из БД
     *
     * @param parameterName Название параметра
     * @throws SQLException Если произошла ошибка при работе с БД
     */
    public void deleteParameter(String parameterName) throws SQLException {
        String sql = "DELETE FROM parameters WHERE parameter = ?";
        try (Connection conn = DatabaseConnectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, parameterName);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                log.info("✅ Параметр '{}' удалён из БД", parameterName);
            } else {
                log.warn("⚠️ Параметр '{}' не найден для удаления", parameterName);
            }
        } catch (SQLException e) {
            log.error("❌ Ошибка при удалении параметра '{}'", parameterName, e);
            throw e;
        }
    }

    /**
     * Алиас для getParameterValue() (для обратной совместимости с OrdersScheduler и AccountService)
     */
    public String getParameter(String parameterName) throws SQLException {
        return getParameterValue(parameterName);
    }

    /**
     * Алиас для setParameterValue() (для обратной совместимости с AccountService)
     */
    public void saveParameter(String parameterName, String value) throws SQLException {
        setParameterValue(parameterName, value);
    }
}
