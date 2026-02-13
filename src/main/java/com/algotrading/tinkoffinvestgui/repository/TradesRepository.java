package com.algotrading.tinkoffinvestgui.repository;

import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TradesRepository {

    private static final Logger log = LoggerFactory.getLogger(TradesRepository.class);

    private Connection getConnection() throws SQLException {
        String dbUrl = ConnectorConfig.getPropertyValue("db.url");
        String dbUser = ConnectorConfig.getPropertyValue("db.username");
        String dbPassword = ConnectorConfig.getPropertyValue("db.password");
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Сохранить новую сделку (INSERT или UPDATE если уже существует)
     */
    public void save(Trade trade) {
        String sql = """
                INSERT INTO public.trades (
                    trade_id, order_id, account_id,
                    figi, ticker, instrument_name, instrument_type,
                    direction, quantity, price,
                    trade_amount, commission, aci, yield_value,
                    trade_date, currency, exchange, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (trade_id) DO UPDATE SET
                    order_id = EXCLUDED.order_id,
                    ticker = EXCLUDED.ticker,
                    instrument_name = EXCLUDED.instrument_name,
                    quantity = EXCLUDED.quantity,
                    price = EXCLUDED.price,
                    trade_amount = EXCLUDED.trade_amount,
                    commission = EXCLUDED.commission,
                    aci = EXCLUDED.aci,
                    yield_value = EXCLUDED.yield_value,
                    updated_at = now()
                """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, trade.getTradeId());
            pstmt.setString(2, trade.getOrderId());
            pstmt.setString(3, trade.getAccountId());
            pstmt.setString(4, trade.getFigi());
            pstmt.setString(5, trade.getTicker());
            pstmt.setString(6, trade.getInstrumentName());
            pstmt.setString(7, trade.getInstrumentType());
            pstmt.setString(8, trade.getDirection());
            pstmt.setLong(9, trade.getQuantity());
            pstmt.setBigDecimal(10, trade.getPrice());
            pstmt.setBigDecimal(11, trade.getTradeAmount());
            pstmt.setBigDecimal(12, trade.getCommission());
            pstmt.setBigDecimal(13, trade.getAci());
            pstmt.setBigDecimal(14, trade.getYieldValue());
            pstmt.setTimestamp(15, Timestamp.from(trade.getTradeDate()));
            pstmt.setString(16, trade.getCurrency());
            pstmt.setString(17, trade.getExchange());
            pstmt.setTimestamp(18, Timestamp.from(Instant.now()));

            pstmt.executeUpdate();
            log.debug("Сделка сохранена: {} ({} {} @ {})",
                    trade.getTradeId(), trade.getDirection(), trade.getQuantity(), trade.getPrice());
        } catch (SQLException e) {
            log.error("Ошибка сохранения сделки", e);
            throw new RuntimeException("Ошибка БД при сохранении сделки: " + e.getMessage(), e);
        }
    }

    /**
     * Найти сделку по trade_id
     */
    public Trade findByTradeId(String tradeId) {
        String sql = "SELECT * FROM public.trades WHERE trade_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tradeId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToTrade(rs);
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка получения сделки по trade_id", e);
        }
        return null;
    }

    /**
     * Все сделки за сегодня
     */
    /**
     * Все сделки за сегодня
     */
    public List<Trade> findTodayTrades() {
        // Используем UTC для сравнения или локальную дату
        String sql = """
            SELECT * FROM public.trades
            WHERE trade_date >= CURRENT_DATE::timestamp
              AND trade_date < (CURRENT_DATE + INTERVAL '1 day')::timestamp
            ORDER BY trade_date DESC
            """;

        List<Trade> trades = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                trades.add(mapResultSetToTrade(rs));
            }

            log.info("📊 Найдено сделок за сегодня: {}", trades.size());
        } catch (SQLException e) {
            log.error("Ошибка получения сделок за сегодня", e);
        }
        return trades;
    }


    /**
     * Сделки за последние N дней
     */
    public List<Trade> findRecentTrades(int days) {
        String sql = """
                SELECT * FROM public.trades
                WHERE trade_date >= now() - (? * interval '1 day')
                ORDER BY trade_date DESC
                """;

        List<Trade> trades = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, days);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    trades.add(mapResultSetToTrade(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка получения сделок за {} дней", days, e);
        }
        return trades;
    }

    /**
     * Сделки по конкретному FIGI
     */
    public List<Trade> findByFigi(String figi) {
        String sql = """
                SELECT * FROM public.trades
                WHERE figi = ?
                ORDER BY trade_date DESC
                """;

        List<Trade> trades = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, figi);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    trades.add(mapResultSetToTrade(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка получения сделок по FIGI", e);
        }
        return trades;
    }

    private Trade mapResultSetToTrade(ResultSet rs) throws SQLException {
        Trade trade = new Trade();

        trade.setId(rs.getInt("id"));
        trade.setTradeId(rs.getString("trade_id"));
        trade.setOrderId(rs.getString("order_id"));
        trade.setAccountId(rs.getString("account_id"));
        trade.setFigi(rs.getString("figi"));
        trade.setTicker(rs.getString("ticker"));
        trade.setInstrumentName(rs.getString("instrument_name"));
        trade.setInstrumentType(rs.getString("instrument_type"));
        trade.setDirection(rs.getString("direction"));
        trade.setQuantity(rs.getLong("quantity"));
        trade.setPrice(rs.getBigDecimal("price"));
        trade.setTradeAmount(rs.getBigDecimal("trade_amount"));
        trade.setCommission(rs.getBigDecimal("commission"));
        trade.setAci(rs.getBigDecimal("aci"));
        trade.setYieldValue(rs.getBigDecimal("yield_value"));

        Timestamp tradeDate = rs.getTimestamp("trade_date");
        if (tradeDate != null) {
            trade.setTradeDate(tradeDate.toInstant());
        }

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            trade.setCreatedAt(createdAt.toInstant());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            trade.setUpdatedAt(updatedAt.toInstant());
        }

        trade.setCurrency(rs.getString("currency"));
        trade.setExchange(rs.getString("exchange"));

        return trade;
    }
}
