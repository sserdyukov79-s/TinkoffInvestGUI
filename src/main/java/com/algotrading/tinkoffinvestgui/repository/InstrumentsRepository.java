package com.algotrading.tinkoffinvestgui.repository;

import com.algotrading.tinkoffinvestgui.config.AppConstants;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import com.algotrading.tinkoffinvestgui.db.DatabaseConnection;

public class InstrumentsRepository {
    private static final Logger log = LoggerFactory.getLogger(InstrumentsRepository.class);

    private Connection getConnection() throws SQLException {
        String dbUrl = ConnectorConfig.getPropertyValue("db.url");
        String dbUser = ConnectorConfig.getPropertyValue("db.username");
        String dbPassword = ConnectorConfig.getPropertyValue("db.password");
        log.debug("Подключение к БД: {}", dbUrl);
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    public List<Instrument> findAll() {
        log.info("Получение всех инструментов из БД...");
        List<Instrument> instruments = new ArrayList<>();

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
                "manual_buy_price, manual_sell_price) " +
                "VALUES (current_date, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

  //          pstmt.setDate(1, Date.valueOf(instrument.getBookdate()));
            pstmt.setString(1, instrument.getFigi());
            pstmt.setString(2, instrument.getName());
            pstmt.setString(3, instrument.getIsin());
            pstmt.setInt(4, instrument.getPriority());
            pstmt.setBigDecimal(5, instrument.getBuyPrice());

            if (instrument.getBuyQuantity() != null) {
                pstmt.setInt(6, instrument.getBuyQuantity());
            } else {
                pstmt.setNull(6, Types.INTEGER);
            }

            pstmt.setBigDecimal(7, instrument.getSellPrice());

            if (instrument.getSellQuantity() != null) {
                pstmt.setInt(8, instrument.getSellQuantity());
            } else {
                pstmt.setNull(8, Types.INTEGER);
            }

            // ✅ НОВЫЕ ПОЛЯ
            pstmt.setBigDecimal(9, instrument.getManualBuyPrice());
            pstmt.setBigDecimal(10, instrument.getManualSellPrice());

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
                "bookdate = current_date, figi = ?, name = ?, isin = ?, priority = ?, " +
                "buy_price = ?, buy_quantity = ?, sell_price = ?, sell_quantity = ?, " +
                "manual_buy_price = ?, manual_sell_price = ? " +
                "WHERE id = ?";


        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

  //          pstmt.setDate(1, Date.valueOf(instrument.getBookdate()));
            pstmt.setString(1, instrument.getFigi());
            pstmt.setString(2, instrument.getName());
            pstmt.setString(3, instrument.getIsin());
            pstmt.setInt(4, instrument.getPriority());
            pstmt.setBigDecimal(5, instrument.getBuyPrice());

            if (instrument.getBuyQuantity() != null) {
                pstmt.setInt(6, instrument.getBuyQuantity());
            } else {
                pstmt.setNull(6, Types.INTEGER);
            }

            pstmt.setBigDecimal(7, instrument.getSellPrice());

            if (instrument.getSellQuantity() != null) {
                pstmt.setInt(8, instrument.getSellQuantity());
            } else {
                pstmt.setNull(8, Types.INTEGER);
            }

            // ✅ НОВЫЕ ПОЛЯ
            pstmt.setBigDecimal(9, instrument.getManualBuyPrice());
            pstmt.setBigDecimal(10, instrument.getManualSellPrice());
            pstmt.setInt(11, instrument.getId());

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

    // ✅ ОБНОВЛЁННЫЙ МАППИНГ
    private Instrument mapResultSetToInstrument(ResultSet rs) throws SQLException {
        Instrument instrument = new Instrument();
        instrument.setId(rs.getInt("id"));
/*
        Date bookdateDate = rs.getDate("bookdate");
        if (bookdateDate != null) {
            instrument.setBookdate(bookdateDate.toLocalDate());
        }
*/
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

        // ✅ ДОБАВЛЕНЫ manual_buy_price, manual_sell_price
        String sql = "SELECT id, figi, name, isin, priority, " +
                "buy_price, buy_quantity, sell_price, sell_quantity, " +
                "manual_buy_price, manual_sell_price " +
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
}
