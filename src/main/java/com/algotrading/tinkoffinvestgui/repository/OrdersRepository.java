package com.algotrading.tinkoffinvestgui.repository;

import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.OrderDirection;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Репозиторий для работы с таблицей public.orders.
 */
public class OrdersRepository {

    private static final Logger log = LoggerFactory.getLogger(OrdersRepository.class);

    private Connection getConnection() throws SQLException {
        String dbUrl = ConnectorConfig.getPropertyValue("db.url");
        String dbUser = ConnectorConfig.getPropertyValue("db.username");
        String dbPassword = ConnectorConfig.getPropertyValue("db.password");
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Сохранить новую заявку.
     */
    public void save(Order order) {
        String sql = """
                INSERT INTO public.orders (
                    my_order_id,
                    exchange_order_id,
                    account_id,
                    figi,
                    ticker,
                    instrument_name,
                    direction,
                    order_type,
                    lots_requested,
                    lots_executed,
                    price,
                    average_execution_price,
                    status,
                    total_order_amount,
                    commission,
                    aci,
                    parent_order_id,
                    parent_fill_time,
                    error_message,
                    created_at,
                    submitted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, order.getMyOrderId());
            pstmt.setString(2, order.getExchangeOrderId());
            pstmt.setString(3, order.getAccountId());
            pstmt.setString(4, order.getFigi());
            pstmt.setString(5, order.getTicker());
            pstmt.setString(6, order.getInstrumentName());
            pstmt.setString(7, order.getDirection() != null ? order.getDirection().name() : null);
            pstmt.setString(8, order.getOrderType());
            pstmt.setLong(9, order.getLotsRequested());
            pstmt.setLong(10, order.getLotsExecuted());
            pstmt.setBigDecimal(11, order.getPrice());
            pstmt.setBigDecimal(12, order.getAverageExecutionPrice());
            pstmt.setString(13, order.getStatus());
            pstmt.setBigDecimal(14, order.getTotalOrderAmount());
            pstmt.setBigDecimal(15, order.getCommission());
            pstmt.setBigDecimal(16, order.getAci());
            pstmt.setString(17, order.getParentOrderId());

            if (order.getParentFillTime() != null) {
                pstmt.setTimestamp(18, Timestamp.from(order.getParentFillTime()));
            } else {
                pstmt.setTimestamp(18, null);
            }

            pstmt.setString(19, order.getErrorMessage());

            Instant createdAt = order.getCreatedAt() != null ? order.getCreatedAt() : Instant.now();
            pstmt.setTimestamp(20, Timestamp.from(createdAt));

            if (order.getSubmittedAt() != null) {
                pstmt.setTimestamp(21, Timestamp.from(order.getSubmittedAt()));
            } else {
                pstmt.setTimestamp(21, null);
            }

            pstmt.executeUpdate();
            log.info("Заявка сохранена: {} ({})", order.getMyOrderId(), order.getDirection());
        } catch (SQLException e) {
            log.error("Ошибка сохранения заявки", e);
            throw new RuntimeException("Ошибка БД при сохранении заявки: " + e.getMessage(), e);
        }
    }

    /**
     * Обновить существующую заявку по my_order_id.
     */
    public void update(Order order) {
        String sql = """
            UPDATE public.orders SET
                exchange_order_id = ?,
                lots_executed = ?,
                average_execution_price = ?,
                status = ?,
                total_order_amount = ?,
                commission = ?,
                error_message = ?,
                submitted_at = ?,
                executed_at = CASE
                    WHEN status = 'FILLED' AND executed_at IS NULL THEN now()
                    ELSE executed_at
                END,
                cancelled_at = CASE
                    WHEN status = 'CANCELLED' AND cancelled_at IS NULL THEN now()
                    ELSE cancelled_at
                END,
                updated_at = now()
            WHERE my_order_id = ?
            """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, order.getExchangeOrderId());
            pstmt.setLong(2, order.getLotsExecuted());
            pstmt.setBigDecimal(3, order.getAverageExecutionPrice());
            pstmt.setString(4, order.getStatus());
            pstmt.setBigDecimal(5, order.getTotalOrderAmount());
            pstmt.setBigDecimal(6, order.getCommission());
            pstmt.setString(7, order.getErrorMessage());

            // >>> ДОБАВЛЯЕМ submitted_at
            if (order.getSubmittedAt() != null) {
                pstmt.setTimestamp(8, Timestamp.from(order.getSubmittedAt()));
            } else {
                pstmt.setTimestamp(8, null);
            }

            pstmt.setString(9, order.getMyOrderId());

            pstmt.executeUpdate();
            log.debug("Заявка обновлена: {} статус={}", order.getMyOrderId(), order.getStatus());
        } catch (SQLException e) {
            log.error("Ошибка обновления заявки", e);
            throw new RuntimeException("Ошибка БД при обновлении заявки: " + e.getMessage(), e);
        }
    }


    public Order findByMyOrderId(String myOrderId) {
        String sql = "SELECT * FROM public.orders WHERE my_order_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, myOrderId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToOrder(rs);
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка получения заявки по my_order_id", e);
        }
        return null;
    }

    public Order findByExchangeOrderId(String exchangeOrderId) {
        String sql = "SELECT * FROM public.orders WHERE exchange_order_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, exchangeOrderId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToOrder(rs);
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка получения заявки по exchange_order_id", e);
        }
        return null;
    }

    /**
     * Заявки с заданным статусом (например, 'PENDING').
     */
    public List<Order> findByStatus(String status) {
        String sql = "SELECT * FROM public.orders WHERE status = ? ORDER BY created_at DESC";
        List<Order> orders = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapResultSetToOrder(rs));
                }
            }
            log.debug("Найдено заявок со статусом '{}': {}", status, orders.size());
        } catch (SQLException e) {
            log.error("Ошибка получения заявок по статусу", e);
        }
        return orders;
    }

    public List<Order> findByFigi(String figi) {
        String sql = "SELECT * FROM public.orders WHERE figi = ? ORDER BY created_at DESC";
        List<Order> orders = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, figi);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapResultSetToOrder(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка получения заявок по figi", e);
        }
        return orders;
    }

    /**
     * Найти SELL-заявку, связанную с указанной BUY-заявкой.
     */
    public Order findSellOrderByParentBuyId(String parentOrderId) {
        String sql = """
                SELECT * FROM public.orders
                WHERE parent_order_id = ? AND direction = 'SELL'
                ORDER BY created_at DESC
                LIMIT 1
                """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, parentOrderId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToOrder(rs);
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка получения SELL-заявки по parent_order_id", e);
        }
        return null;
    }

    /**
     * Активные заявки (NEW, PARTIALLY_FILLED, PENDING).
     */
    public List<Order> findActiveOrders() {
        String sql = """
                SELECT * FROM public.orders
                WHERE status IN ('NEW', 'PARTIALLY_FILLED', 'PENDING')
                ORDER BY created_at DESC
                """;

        List<Order> orders = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                orders.add(mapResultSetToOrder(rs));
            }
        } catch (SQLException e) {
            log.error("Ошибка получения активных заявок", e);
        }
        return orders;
    }

    /**
     * История заявок за последние days дней.
     */
    public List<Order> findHistory(int days) {
        String sql = """
                SELECT * FROM public.orders
                WHERE created_at >= now() - (? * interval '1 day')
                ORDER BY created_at DESC
                """;

        List<Order> orders = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, days);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapResultSetToOrder(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка получения истории заявок", e);
        }
        return orders;
    }
    /**
     * Заявки, созданные сегодня (с начала текущего дня).
     */
    public List<Order> findTodayOrders() {
        String sql = """
            SELECT * FROM public.orders
            WHERE created_at::date = CURRENT_DATE
            ORDER BY created_at DESC
            """;

        List<Order> orders = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                orders.add(mapResultSetToOrder(rs));
            }
            log.debug("Найдено заявок за сегодня: {}", orders.size());
        } catch (SQLException e) {
            log.error("Ошибка получения сегодняшних заявок", e);
        }
        return orders;
    }

    /**
     * Проверка: есть ли сегодня активная заявка по FIGI + direction.
     */
    public boolean hasActiveTodayOrder(String figi, String direction) {
        String sql = """
                SELECT COUNT(*) FROM public.orders
                WHERE figi = ?
                  AND direction = ?
                  AND status IN (
                      'PENDING',
                      'NEW',
                      'PARTIALLY_FILLED',
                      'EXECUTION_REPORT_STATUS_NEW',
                      'EXECUTION_REPORT_STATUS_PARTIALLYFILL'
                  )
                  AND created_at::date = CURRENT_DATE
                """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, figi);
            pstmt.setString(2, direction);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count > 0) {
                        log.debug("Найдены {} активных заявок сегодня по figi={} direction={}", count, figi, direction);
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка проверки активных заявок по figi/ direction", e);
        }
        return false;
    }

    private Order mapResultSetToOrder(ResultSet rs) throws SQLException {
        Order order = new Order();

        order.setId(rs.getInt("id"));
        order.setMyOrderId(rs.getString("my_order_id"));
        order.setExchangeOrderId(rs.getString("exchange_order_id"));
        order.setAccountId(rs.getString("account_id"));
        order.setFigi(rs.getString("figi"));
        order.setTicker(rs.getString("ticker"));
        order.setInstrumentName(rs.getString("instrument_name"));

        String directionStr = rs.getString("direction");
        if (directionStr != null) {
            try {
                order.setDirection(OrderDirection.valueOf(directionStr));
            } catch (IllegalArgumentException e) {
                log.warn("Неизвестное значение direction в БД: {}", directionStr);
            }
        }

        order.setOrderType(rs.getString("order_type"));
        order.setLotsRequested(rs.getLong("lots_requested"));
        order.setLotsExecuted(rs.getLong("lots_executed"));
        order.setPrice(rs.getBigDecimal("price"));
        order.setAverageExecutionPrice(rs.getBigDecimal("average_execution_price"));
        order.setStatus(rs.getString("status"));
        order.setTotalOrderAmount(rs.getBigDecimal("total_order_amount"));
        order.setCommission(rs.getBigDecimal("commission"));
        order.setAci(rs.getBigDecimal("aci"));
        order.setParentOrderId(rs.getString("parent_order_id"));

        Timestamp parentFillTime = rs.getTimestamp("parent_fill_time");
        if (parentFillTime != null) {
            order.setParentFillTime(parentFillTime.toInstant());
        }

        order.setErrorMessage(rs.getString("error_message"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            order.setCreatedAt(createdAt.toInstant());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            order.setUpdatedAt(updatedAt.toInstant());
        }

        Timestamp executedAt = rs.getTimestamp("executed_at");
        if (executedAt != null) {
            order.setExecutedAt(executedAt.toInstant());
        }

        Timestamp cancelledAt = rs.getTimestamp("cancelled_at");
        if (cancelledAt != null) {
            order.setCancelledAt(cancelledAt.toInstant());
        }

        Timestamp submittedAt = null;
        try {
            submittedAt = rs.getTimestamp("submitted_at");
        } catch (SQLException ignored) {
            // Для старых БД без этого поля
        }
        if (submittedAt != null) {
            order.setSubmittedAt(submittedAt.toInstant());
        }

        return order;
    }

    /**
     * Простая агрегированная статистика по заявкам.
     */
    public OrderStatistics getStatistics() {
        String sql = """
                SELECT
                    COUNT(*) as total,
                    COUNT(CASE WHEN direction = 'BUY' THEN 1 END) as buy_count,
                    COUNT(CASE WHEN direction = 'SELL' THEN 1 END) as sell_count,
                    COUNT(CASE WHEN status = 'FILLED' THEN 1 END) as filled_count,
                    SUM(CASE WHEN status = 'FILLED' AND direction = 'BUY'
                        THEN total_order_amount ELSE 0 END) as total_bought,
                    SUM(CASE WHEN status = 'FILLED' AND direction = 'SELL'
                        THEN total_order_amount ELSE 0 END) as total_sold
                FROM public.orders
                """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return new OrderStatistics(
                        rs.getInt("total"),
                        rs.getInt("buy_count"),
                        rs.getInt("sell_count"),
                        rs.getInt("filled_count"),
                        rs.getBigDecimal("total_bought"),
                        rs.getBigDecimal("total_sold")
                );
            }
        } catch (SQLException e) {
            log.error("Ошибка получения статистики по заявкам", e);
        }

        return new OrderStatistics();
    }

    public static class OrderStatistics {
        public int total;
        public int buyCount;
        public int sellCount;
        public int filledCount;
        public BigDecimal totalBought;
        public BigDecimal totalSold;

        public OrderStatistics() {
        }

        public OrderStatistics(int total,
                               int buyCount,
                               int sellCount,
                               int filledCount,
                               BigDecimal totalBought,
                               BigDecimal totalSold) {
            this.total = total;
            this.buyCount = buyCount;
            this.sellCount = sellCount;
            this.filledCount = filledCount;
            this.totalBought = totalBought;
            this.totalSold = totalSold;
        }

        @Override
        public String toString() {
            return String.format(
                    "Всего: %d | BUY: %d | SELL: %d | Заполнено: %d | Куплено: %s | Продано: %s",
                    total, buyCount, sellCount, filledCount, totalBought, totalSold
            );
        }
    }
}
