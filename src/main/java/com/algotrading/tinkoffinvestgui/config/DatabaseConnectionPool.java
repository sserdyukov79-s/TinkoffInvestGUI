package com.algotrading.tinkoffinvestgui.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Connection Pool для PostgreSQL с использованием HikariCP
 */
public class DatabaseConnectionPool {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionPool.class);

    private static HikariDataSource dataSource;

    static {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(ConnectorConfig.getPropertyValue("db.url"));
            config.setUsername(ConnectorConfig.getPropertyValue("db.username"));
            config.setPassword(ConnectorConfig.getPropertyValue("db.password"));

            // Оптимальные настройки пула
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            // Дополнительные настройки
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);

            log.info("✅ Connection Pool инициализирован (Max Pool Size: {})",
                    config.getMaximumPoolSize());

        } catch (Exception e) {
            log.error("❌ Ошибка инициализации Connection Pool", e);
            throw new RuntimeException("Не удалось создать Connection Pool", e);
        }
    }

    /**
     * Получает соединение из пула
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Получает DataSource
     */
    public static DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Закрывает Connection Pool (вызывать при завершении приложения)
     */
    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            log.info("Закрытие Connection Pool");
            dataSource.close();
        }
    }

    /**
     * Возвращает статистику пула
     */
    public static String getPoolStats() {
        if (dataSource != null) {
            return String.format(
                    "Active: %d, Idle: %d, Total: %d, Waiting: %d",
                    dataSource.getHikariPoolMXBean().getActiveConnections(),
                    dataSource.getHikariPoolMXBean().getIdleConnections(),
                    dataSource.getHikariPoolMXBean().getTotalConnections(),
                    dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
            );
        }
        return "Pool not initialized";
    }
}
