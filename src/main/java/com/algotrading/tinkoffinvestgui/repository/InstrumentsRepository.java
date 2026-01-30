package com.algotrading.tinkoffinvestgui.repository;

import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository для работы с таблицей instruments
 */
public class InstrumentsRepository {

    // ✅ ДОБАВЛЯЕМ ЛОГГЕР
    private static final Logger log = LoggerFactory.getLogger(InstrumentsRepository.class);

    private Connection getConnection() throws SQLException {
        String dbUrl = ConnectorConfig.getPropertyValue("db.url");
        String dbUser = ConnectorConfig.getPropertyValue("db.username");
        String dbPassword = ConnectorConfig.getPropertyValue("db.password");
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Получает все инструменты, отсортированные по приоритету и названию
     */
    public List<Instrument> findAll() {
        List<Instrument> instruments = new ArrayList<>();
        String sql = "SELECT * FROM public.instruments ORDER BY bookdate DESC, priority, name";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                instruments.add(mapResultSetToInstrument(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения инструментов: " + e.getMessage(), e);
        }

        return instruments;
    }

    /**
     * Получает инструменты по конкретной дате
     */
    public List<Instrument> findByBookdate(LocalDate bookdate) {
        List<Instrument> instruments = new ArrayList<>();
        String sql = "SELECT * FROM public.instruments WHERE bookdate = ? ORDER BY priority, name";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(bookdate));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                instruments.add(mapResultSetToInstrument(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения инструментов по дате: " + e.getMessage(), e);
        }

        return instruments;
    }

    /**
     * Добавляет новый инструмент
     */
    public void save(Instrument instrument) {
        String sql = """
            INSERT INTO public.instruments
            (bookdate, figi, name, isin, priority, buy_price, buy_quantity, sell_price, sell_quantity)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(instrument.getBookdate()));
            pstmt.setString(2, instrument.getFigi());
            pstmt.setString(3, instrument.getName());
            pstmt.setString(4, instrument.getIsin());
            pstmt.setInt(5, instrument.getPriority());
            pstmt.setBigDecimal(6, instrument.getBuyPrice());

            if (instrument.getBuyQuantity() != null) {
                pstmt.setInt(7, instrument.getBuyQuantity());
            } else {
                pstmt.setNull(7, Types.INTEGER);
            }

            pstmt.setBigDecimal(8, instrument.getSellPrice());

            if (instrument.getSellQuantity() != null) {
                pstmt.setInt(9, instrument.getSellQuantity());
            } else {
                pstmt.setNull(9, Types.INTEGER);
            }

            pstmt.executeUpdate();
            log.info("✓ Инструмент добавлен: {}", instrument.getName());

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка сохранения инструмента: " + e.getMessage(), e);
        }
    }

    /**
     * Обновляет существующий инструмент
     */
    public void update(Instrument instrument) {
        String sql = """
            UPDATE public.instruments
            SET bookdate = ?, figi = ?, name = ?, isin = ?, priority = ?,
                buy_price = ?, buy_quantity = ?, sell_price = ?, sell_quantity = ?
            WHERE id = ?
            """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(instrument.getBookdate()));
            pstmt.setString(2, instrument.getFigi());
            pstmt.setString(3, instrument.getName());
            pstmt.setString(4, instrument.getIsin());
            pstmt.setInt(5, instrument.getPriority());
            pstmt.setBigDecimal(6, instrument.getBuyPrice());

            if (instrument.getBuyQuantity() != null) {
                pstmt.setInt(7, instrument.getBuyQuantity());
            } else {
                pstmt.setNull(7, Types.INTEGER);
            }

            pstmt.setBigDecimal(8, instrument.getSellPrice());

            if (instrument.getSellQuantity() != null) {
                pstmt.setInt(9, instrument.getSellQuantity());
            } else {
                pstmt.setNull(9, Types.INTEGER);
            }

            pstmt.setInt(10, instrument.getId());
            pstmt.executeUpdate();
            log.info("✓ Инструмент обновлён: {}", instrument.getName());

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка обновления инструмента: " + e.getMessage(), e);
        }
    }

    /**
     * Удаляет инструмент по ID
     */
    public void delete(int id) {
        String sql = "DELETE FROM public.instruments WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            log.info("✓ Инструмент удалён (ID: {})", id);

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка удаления инструмента: " + e.getMessage(), e);
        }
    }

    /**
     * Преобразует ResultSet в Instrument
     */
    private Instrument mapResultSetToInstrument(ResultSet rs) throws SQLException {
        Instrument instrument = new Instrument();
        instrument.setId(rs.getInt("id"));

        Date bookdateDate = rs.getDate("bookdate");
        if (bookdateDate != null) {
            instrument.setBookdate(bookdateDate.toLocalDate());
        }

        instrument.setFigi(rs.getString("figi"));
        instrument.setName(rs.getString("name"));
        instrument.setIsin(rs.getString("isin"));
        instrument.setPriority(rs.getInt("priority"));
        instrument.setBuyPrice(rs.getBigDecimal("buy_price"));
        instrument.setBuyQuantity((Integer) rs.getObject("buy_quantity"));
        instrument.setSellPrice(rs.getBigDecimal("sell_price"));
        instrument.setSellQuantity((Integer) rs.getObject("sell_quantity"));

        return instrument;
    }

    /**
     * Подсчитывает количество инструментов
     */
    public int count() {
        String sql = "SELECT COUNT(*) FROM public.instruments";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка подсчета инструментов: " + e.getMessage(), e);
        }

        return 0;
    }

    /**
     * Получает последнюю использованную дату
     */
    public LocalDate getLatestBookdate() {
        String sql = "SELECT MAX(bookdate) FROM public.instruments";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                Date date = rs.getDate(1);
                return date != null ? date.toLocalDate() : LocalDate.now();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения последней даты: " + e.getMessage(), e);
        }

        return LocalDate.now();
    }
}
