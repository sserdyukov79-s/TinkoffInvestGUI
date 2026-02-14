package com.algotrading.tinkoffinvestgui.repository;

import com.algotrading.tinkoffinvestgui.config.AppConstants;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.db.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class InstrumentsRepository {

    private static final Logger log = LoggerFactory.getLogger(InstrumentsRepository.class);

    private Connection getConnection() throws SQLException {
        String dbUrl = ConnectorConfig.getPropertyValue("db.url");
        String dbUser = ConnectorConfig.getPropertyValue("db.username");
        String dbPassword = ConnectorConfig.getPropertyValue("db.password");
        log.debug("Подключение к БД: {}", dbUrl);
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    @SuppressWarnings("rawtypes")
    public List findAll() {
        log.info("Получение всех инструментов из БД...");
        List instruments = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(AppConstants.SQL_SELECT_ALL_INSTRUMENTS)) {

            log.debug("Выполнен SQL: {}", AppConstants.SQL_SELECT_ALL_INSTRUMENTS);
            while (rs.next()) {
                Instrument instrument = mapResultSetToInstrument(rs);
                instruments.add(instrument);
                log.debug("Загружен инструмент: {} (ISIN: {})",
                        instrument.getName(), instrument.getIsin());
            }
            log.info("Загружено инструментов: {}", instruments.size());
        } catch (SQLException e) {
            log.error("Ошибка при получении инструментов", e);
            log.error("SQL: {}", AppConstants.SQL_SELECT_ALL_INSTRUMENTS);
            log.error("Детали: {}", e.getMessage());
            throw new RuntimeException("Ошибка БД: " + e.getMessage(), e);
        }
        return instruments;
    }

    public void save(Instrument instrument) {
        log.info("Сохранение инструмента: {}", instrument.getName());

        String sql = "INSERT INTO public.instruments " +
                "(bookdate, figi, name, isin, priority, " +
                "buy_price, buy_quantity, sell_price, sell_quantity, " +
                "manual_buy_price, manual_sell_price, " +
                "sell_price_fixed, sell_price_fixed_date) " +
                "VALUES (current_date, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // 1: figi
            pstmt.setString(1, instrument.getFigi());
            // 2: name
            pstmt.setString(2, instrument.getName());
            // 3: isin
            pstmt.setString(3, instrument.getIsin());
            // 4: priority
            pstmt.setInt(4, instrument.getPriority());
            // 5: buy_price
            pstmt.setBigDecimal(5, instrument.getBuyPrice());
            // 6: buy_quantity
            if (instrument.getBuyQuantity() != null) {
                pstmt.setInt(6, instrument.getBuyQuantity());
            } else {
                pstmt.setNull(6, Types.INTEGER);
            }
            // 7: sell_price
            pstmt.setBigDecimal(7, instrument.getSellPrice());
            // 8: sell_quantity
            if (instrument.getSellQuantity() != null) {
                pstmt.setInt(8, instrument.getSellQuantity());
            } else {
                pstmt.setNull(8, Types.INTEGER);
            }
            // 9: manual_buy_price
            pstmt.setBigDecimal(9, instrument.getManualBuyPrice());
            // 10: manual_sell_price
            pstmt.setBigDecimal(10, instrument.getManualSellPrice());
            // 11: sell_price_fixed
            if (instrument.getSellPriceFixed() != null) {
                pstmt.setBigDecimal(11, instrument.getSellPriceFixed());
            } else {
                pstmt.setNull(11, Types.DECIMAL);
            }
            // 12: sell_price_fixed_date
            if (instrument.getSellPriceFixedDate() != null) {
                pstmt.setDate(12, Date.valueOf(instrument.getSellPriceFixedDate()));
            } else {
                pstmt.setNull(12, Types.DATE);
            }

            pstmt.executeUpdate();
            log.info("Инструмент сохранён: {}", instrument.getName());
        } catch (SQLException e) {
            log.error("Ошибка при сохранении инструмента", e);
            throw new RuntimeException("Ошибка БД: " + e.getMessage(), e);
        }
    }

    public void update(Instrument instrument) {
        log.info("Обновление инструмента: {}", instrument.getName());

        String sql = "UPDATE public.instruments SET " +
                "bookdate = current_date, " +
                "figi = ?, name = ?, isin = ?, priority = ?, " +
                "buy_price = ?, buy_quantity = ?, " +
                "sell_price = ?, sell_quantity = ?, " +
                "manual_buy_price = ?, manual_sell_price = ?, " +
                "sell_price_fixed = ?, sell_price_fixed_date = ? " +
                "WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // 1: figi
            pstmt.setString(1, instrument.getFigi());
            // 2: name
            pstmt.setString(2, instrument.getName());
            // 3: isin
            pstmt.setString(3, instrument.getIsin());
            // 4: priority
            pstmt.setInt(4, instrument.getPriority());

            // 5: buy_price
            if (instrument.getBuyPrice() != null) {
                pstmt.setBigDecimal(5, instrument.getBuyPrice());
            } else {
                pstmt.setNull(5, Types.DECIMAL);
            }

            // 6: buy_quantity
            if (instrument.getBuyQuantity() != null) {
                pstmt.setInt(6, instrument.getBuyQuantity());
            } else {
                pstmt.setNull(6, Types.INTEGER);
            }

            // 7: sell_price
            if (instrument.getSellPrice() != null) {
                pstmt.setBigDecimal(7, instrument.getSellPrice());
            } else {
                pstmt.setNull(7, Types.DECIMAL);
            }

            // 8: sell_quantity
            if (instrument.getSellQuantity() != null) {
                pstmt.setInt(8, instrument.getSellQuantity());
            } else {
                pstmt.setNull(8, Types.INTEGER);
            }

            // 9: manual_buy_price
            if (instrument.getManualBuyPrice() != null) {
                pstmt.setBigDecimal(9, instrument.getManualBuyPrice());
            } else {
                pstmt.setNull(9, Types.DECIMAL);
            }

            // 10: manual_sell_price
            if (instrument.getManualSellPrice() != null) {
                pstmt.setBigDecimal(10, instrument.getManualSellPrice());
            } else {
                pstmt.setNull(10, Types.DECIMAL);
            }

            // 11: sell_price_fixed
            if (instrument.getSellPriceFixed() != null) {
                pstmt.setBigDecimal(11, instrument.getSellPriceFixed());
            } else {
                pstmt.setNull(11, Types.DECIMAL);
            }

            // 12: sell_price_fixed_date
            if (instrument.getSellPriceFixedDate() != null) {
                pstmt.setDate(12, Date.valueOf(instrument.getSellPriceFixedDate()));
            } else {
                pstmt.setNull(12, Types.DATE);
            }

            // 13: id
            pstmt.setInt(13, instrument.getId());

            log.debug("MY manual_buy_price: {} ", pstmt);
            log.debug("buy_price: {} (null={})", instrument.getBuyPrice(), instrument.getBuyPrice() == null);
            log.debug("manual_buy_price: {} (null={})", instrument.getManualBuyPrice(), instrument.getManualBuyPrice() == null);

            pstmt.executeUpdate();
            log.info("Инструмент обновлён: {}", instrument.getName());
        } catch (SQLException e) {
            log.error("Ошибка при обновлении инструмента", e);
            throw new RuntimeException("Ошибка БД: " + e.getMessage(), e);
        }
    }

    public void delete(int id) {
        log.info("Удаление инструмента ID: {}", id);
        String sql = "DELETE FROM public.instruments WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            log.info("Инструмент удалён ID: {}", id);
        } catch (SQLException e) {
            log.error("Ошибка при удалении инструмента", e);
            throw new RuntimeException("Ошибка БД: " + e.getMessage(), e);
        }
    }

    private Instrument mapResultSetToInstrument(ResultSet rs) throws SQLException {
        Instrument instrument = new Instrument();
        instrument.setId(rs.getInt("id"));
        instrument.setFigi(rs.getString("figi"));
        instrument.setName(rs.getString("name"));
        instrument.setIsin(rs.getString("isin"));
        instrument.setPriority(rs.getInt("priority"));
        instrument.setBuyPrice(rs.getBigDecimal("buy_price"));
        instrument.setBuyQuantity((Integer) rs.getObject("buy_quantity"));
        instrument.setSellPrice(rs.getBigDecimal("sell_price"));
        instrument.setSellQuantity((Integer) rs.getObject("sell_quantity"));
        instrument.setManualBuyPrice(rs.getBigDecimal("manual_buy_price"));
        instrument.setManualSellPrice(rs.getBigDecimal("manual_sell_price"));

        // новые поля
        instrument.setSellPriceFixed(rs.getBigDecimal("sell_price_fixed"));
        Date fixedDate = rs.getDate("sell_price_fixed_date");
        instrument.setSellPriceFixedDate(fixedDate != null ? fixedDate.toLocalDate() : null);

        return instrument;
    }

    public LocalDate getLatestBookdate() {
        log.debug("Получение последней даты бронирования...");
        String sql = "SELECT MAX(bookdate) FROM public.instruments";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                Date date = rs.getDate(1);
                LocalDate result = (date != null) ? date.toLocalDate() : LocalDate.now();
                log.debug("Последняя дата: {}", result);
                return result;
            }
        } catch (SQLException e) {
            log.error("Ошибка при получении последней даты", e);
            throw new RuntimeException("Ошибка БД: " + e.getMessage(), e);
        }
        return LocalDate.now();
    }

    public Instrument findById(int id) {
        log.debug("Поиск инструмента по ID: {}", id);

        String sql = "SELECT id, figi, name, isin, priority, " +
                "buy_price, buy_quantity, sell_price, sell_quantity, " +
                "manual_buy_price, manual_sell_price, " +
                "sell_price_fixed, sell_price_fixed_date " +
                "FROM public.instruments WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Instrument instrument = mapResultSetToInstrument(rs);
                    log.debug("Найден инструмент: {}", instrument.getName());
                    return instrument;
                } else {
                    log.warn("Инструмент не найден ID: {}", id);
                    return null;
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка при поиске инструмента ID: {}", id, e);
            throw new RuntimeException("Ошибка БД: " + e.getMessage(), e);
        }
    }

    public Instrument findByFigi(String figi) {
        log.debug("Поиск инструмента по FIGI: {}", figi);

        String sql = "SELECT id, figi, name, isin, priority, " +
                "buy_price, buy_quantity, sell_price, sell_quantity, " +
                "manual_buy_price, manual_sell_price, " +
                "sell_price_fixed, sell_price_fixed_date " +
                "FROM public.instruments " +
                "WHERE figi = ? " +
                "ORDER BY bookdate DESC " +
                "LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, figi);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Instrument instrument = mapResultSetToInstrument(rs);
                    log.debug("Найден инструмент по FIGI {}: {}", figi, instrument.getName());
                    return instrument;
                } else {
                    log.warn("Инструмент с FIGI {} не найден", figi);
                    return null;
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка при поиске инструмента по FIGI {}", figi, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
