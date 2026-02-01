package com.algotrading.tinkoffinvestgui.repository;

import com.algotrading.tinkoffinvestgui.config.DatabaseConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Репозиторий для работы с таблицей parameters (конфигурационные параметры приложения)
 * ✅ С поддержкой адаптивной маржи на основе волатильности
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
            log.error("❌ Ошибка при установке параметра '{}' в значение '{}'", parameterName, value, e);
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

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // АЛИАСЫ ДЛЯ ОБРАТНОЙ СОВМЕСТИМОСТИ
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ✅ МЕТОДЫ ДЛЯ КОМИССИИ БРОКЕРА
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Получает комиссию брокера из БД в десятичном виде
     *
     * Преобразование: '0.04' в БД (процент) → 0.0004 (десятичное значение)
     *
     * Формула: commissionDecimal = commissionPercent / 100
     *
     * Примеры:
     *  - БД: '0.04' → Результат: 0.0004 (0.04%)
     *  - БД: '0.05' → Результат: 0.0005 (0.05%)
     *  - БД: '0.1'  → Результат: 0.001  (0.1%)
     *
     * @return комиссия в десятичном виде (0.0004 для 0.04%)
     */
    public double getBrokerCommissionDecimal() {
        try {
            String commissionStr = getParameterValue("BROKER_COMMISSION_PERCENT");
            if (commissionStr != null && !commissionStr.isEmpty()) {
                double commissionPercent = Double.parseDouble(commissionStr);
                double commissionDecimal = commissionPercent / 100.0;

                log.debug("✅ Загружена комиссия брокера из БД: {}% ({} в десятичном виде)",
                        commissionPercent, commissionDecimal);

                return commissionDecimal;
            } else {
                log.warn("⚠️ Параметр BROKER_COMMISSION_PERCENT не найден в БД, " +
                        "используется значение по умолчанию 0.04% (0.0004)");
                return 0.0004; // 0.04% по умолчанию
            }
        } catch (NumberFormatException e) {
            log.error("❌ Ошибка парсинга комиссии брокера из БД (некорректный формат числа)", e);
            return 0.0004; // 0.04% по умолчанию при ошибке парсинга
        } catch (Exception e) {
            log.error("❌ Ошибка получения комиссии брокера из БД", e);
            return 0.0004; // 0.04% по умолчанию при любой ошибке
        }
    }

    /**
     * Получает комиссию брокера из БД в процентах
     *
     * Возвращает значение как есть из БД без преобразования
     *
     * Примеры:
     *  - БД: '0.04' → Результат: 0.04
     *  - БД: '0.05' → Результат: 0.05
     *
     * @return комиссия в процентах (0.04 для 0.04%)
     */
    public double getBrokerCommissionPercent() {
        try {
            String commissionStr = getParameterValue("BROKER_COMMISSION_PERCENT");
            if (commissionStr != null && !commissionStr.isEmpty()) {
                double commissionPercent = Double.parseDouble(commissionStr);
                log.debug("✅ Загружена комиссия брокера из БД: {}%", commissionPercent);
                return commissionPercent;
            } else {
                log.warn("⚠️ Параметр BROKER_COMMISSION_PERCENT не найден в БД, " +
                        "используется значение по умолчанию 0.04%");
                return 0.04; // 0.04% по умолчанию
            }
        } catch (NumberFormatException e) {
            log.error("❌ Ошибка парсинга комиссии брокера из БД (некорректный формат числа)", e);
            return 0.04; // 0.04% по умолчанию при ошибке парсинга
        } catch (Exception e) {
            log.error("❌ Ошибка получения комиссии брокера из БД", e);
            return 0.04; // 0.04% по умолчанию при любой ошибке
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ✅ МЕТОДЫ ДЛЯ АДАПТИВНОЙ МАРЖИ
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Получить множитель волатильности для расчёта цены покупки
     *
     * Формула: buyPrice = lastPrice - (VOLATILITY_MULTIPLIER × volatility)
     *
     * @return множитель волатильности (по умолчанию 1.2)
     */
    public double getVolatilityMultiplier() {
        try {
            String value = getParameterValue("VOLATILITY_MULTIPLIER");
            if (value != null && !value.isEmpty()) {
                double multiplier = Double.parseDouble(value);
                log.debug("✅ Загружен множитель волатильности: {}", multiplier);
                return multiplier;
            } else {
                log.warn("⚠️ Параметр VOLATILITY_MULTIPLIER не найден, используется 1.2");
                return 1.2;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка получения VOLATILITY_MULTIPLIER", e);
            return 1.2;
        }
    }

    /**
     * Получить коэффициент влияния волатильности на маржу
     *
     * Формула: profitMargin = VOLATILITY_COEFFICIENT × volatilityPercent
     *
     * @return коэффициент (по умолчанию 0.4)
     */
    public double getVolatilityCoefficient() {
        try {
            String value = getParameterValue("VOLATILITY_COEFFICIENT");
            if (value != null && !value.isEmpty()) {
                double coefficient = Double.parseDouble(value);
                log.debug("✅ Загружен коэффициент адаптивной маржи: {}", coefficient);
                return coefficient;
            } else {
                log.warn("⚠️ Параметр VOLATILITY_COEFFICIENT не найден, используется 0.4");
                return 0.4;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка получения VOLATILITY_COEFFICIENT", e);
            return 0.4;
        }
    }

    /**
     * Получить минимальную маржу прибыли в процентах
     *
     * @return минимальная маржа (по умолчанию 0.3%)
     */
    public double getMinProfitMarginPercent() {
        try {
            String value = getParameterValue("MIN_PROFIT_MARGIN_PERCENT");
            if (value != null && !value.isEmpty()) {
                double minMargin = Double.parseDouble(value);
                log.debug("✅ Загружена минимальная маржа: {}%", minMargin);
                return minMargin;
            } else {
                log.warn("⚠️ Параметр MIN_PROFIT_MARGIN_PERCENT не найден, используется 0.3%");
                return 0.3;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка получения MIN_PROFIT_MARGIN_PERCENT", e);
            return 0.3;
        }
    }

    /**
     * Получить максимальную маржу прибыли в процентах
     *
     * @return максимальная маржа (по умолчанию 2%)
     */
    public double getMaxProfitMarginPercent() {
        try {
            String value = getParameterValue("MAX_PROFIT_MARGIN_PERCENT");
            if (value != null && !value.isEmpty()) {
                double maxMargin = Double.parseDouble(value);
                log.debug("✅ Загружена максимальная маржа: {}%", maxMargin);
                return maxMargin;
            } else {
                log.warn("⚠️ Параметр MAX_PROFIT_MARGIN_PERCENT не найден, используется 2%");
                return 2.0;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка получения MAX_PROFIT_MARGIN_PERCENT", e);
            return 2.0;
        }
    }

    /**
     * Загрузить все параметры стратегии одним вызовом
     *
     * @return объект со всеми параметрами стратегии
     */
    public StrategyParameters getStrategyParameters() {
        StrategyParameters params = new StrategyParameters();

        params.brokerCommission = getBrokerCommissionDecimal();
        params.volatilityMultiplier = getVolatilityMultiplier();
        params.volatilityCoefficient = getVolatilityCoefficient();
        params.minProfitMarginPercent = getMinProfitMarginPercent();
        params.maxProfitMarginPercent = getMaxProfitMarginPercent();

        log.info("📊 Загружены параметры стратегии: комиссия={:.4f}%, множитель_волатильности={}, " +
                        "коэфф_адаптивной_маржи={}, мин_маржа={}%, макс_маржа={}%",
                params.brokerCommission * 100,
                params.volatilityMultiplier,
                params.volatilityCoefficient,
                params.minProfitMarginPercent,
                params.maxProfitMarginPercent);

        return params;
    }

    /**
     * DTO для хранения всех параметров стратегии
     */
    public static class StrategyParameters {
        public double brokerCommission;           // 0.0004 (0.04%)
        public double volatilityMultiplier;       // 1.2
        public double volatilityCoefficient;      // 0.4
        public double minProfitMarginPercent;     // 0.3%
        public double maxProfitMarginPercent;     // 2%
    }
}