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

    private static final Logger log = LoggerFactory.getLogger(InstrumentsRepository.class);

    private Connection getConnection() throws SQLException {
        String dbUrl = ConnectorConfig.getPropertyValue("db.url");
        String dbUser = ConnectorConfig.getPropertyValue("db.username");
        String dbPassword = ConnectorConfig.getPropertyValue("db.password");

        log.debug("🔌 Подключение к БД: {}", dbUrl);

        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Получает все инструменты, отсортированные по приоритету и названию
     */
    public List<Instrument> findAll() {
        log.info("📊 Запрос всех инструментов из БД...");

        List<Instrument> instruments = new ArrayList<>();
        String sql = "SELECT * FROM public.instruments ORDER BY bookdate DESC, priority, name";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            log.debug("✅ SQL выполнен успешно");

            while (rs.next()) {
                Instrument instrument = mapResultSetToInstrument(rs);
                instruments.add(instrument);
                log.debug("  ➜ Загружен: {} (ISIN: {})", instrument.getName(), instrument.getIsin());
            }

            log.info("✅ Загружено инструментов: {}", instruments.size());

        } catch (SQLException e) {
            log.error("❌ Ошибка получения инструментов из БД", e);
            log.error("   SQL: {}", sql);
            log.error("   Сообщение: {}", e.getMessage());
            throw new RuntimeException("Ошибка получения инструментов: " + e.getMessage(), e);
        }

        return instruments;
    }

    /**
     * Получает инструменты по конкретной дате
     */
    public List<Instrument> findByBookdate(LocalDate bookdate) {
        log.info("📊 Запрос инструментов по дате: {}", bookdate);

        List<Instrument> instruments = new ArrayList<>();
        String sql = "SELECT * FROM public.instruments WHERE bookdate = ? ORDER BY priority, name";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(bookdate));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                instruments.add(mapResultSetToInstrument(rs));
            }

            log.info("✅ Загружено инструментов: {}", instruments.size());

        } catch (SQLException e) {
            log.error("❌ Ошибка получения инструментов по дате", e);
            throw new RuntimeException("Ошибка получения инструментов по дате: " + e.getMessage(), e);
        }

        return instruments;
    }

    /**
     * Добавляет новый инструмент
     */
    public void save(Instrument instrument) {
        log.info("💾 Сохранение инструмента: {}", instrument.getName());

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
            log.info("✅ Инструмент добавлен: {}", instrument.getName());

        } catch (SQLException e) {
            log.error("❌ Ошибка сохранения инструмента", e);
            throw new RuntimeException("Ошибка сохранения инструмента: " + e.getMessage(), e);
        }
    }

    /**
     * Обновляет существующий инструмент
     */
    public void update(Instrument instrument) {
        log.info("📝 Обновление инструмента: {}", instrument.getName());

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

            log.info("✅ Инструмент обновлён: {}", instrument.getName());

        } catch (SQLException e) {
            log.error("❌ Ошибка обновления инструмента", e);
            throw new RuntimeException("Ошибка обновления инструмента: " + e.getMessage(), e);
        }
    }

    /**
     * Удаляет инструмент по ID
     */
    public void delete(int id) {
        log.info("🗑️ Удаление инструмента ID: {}", id);

        String sql = "DELETE FROM public.instruments WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();

            log.info("✅ Инструмент удалён (ID: {})", id);

        } catch (SQLException e) {
            log.error("❌ Ошибка удаления инструмента", e);
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
        log.debug("🔢 Подсчёт количества инструментов...");

        String sql = "SELECT COUNT(*) FROM public.instruments";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                int count = rs.getInt(1);
                log.debug("✅ Всего инструментов: {}", count);
                return count;
            }

        } catch (SQLException e) {
            log.error("❌ Ошибка подсчета инструментов", e);
            throw new RuntimeException("Ошибка подсчета инструментов: " + e.getMessage(), e);
        }

        return 0;
    }

    /**
     * Получает последнюю использованную дату
     */
    public LocalDate getLatestBookdate() {
        log.debug("📅 Получение последней даты...");

        String sql = "SELECT MAX(bookdate) FROM public.instruments";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                Date date = rs.getDate(1);
                LocalDate result = date != null ? date.toLocalDate() : LocalDate.now();
                log.debug("✅ Последняя дата: {}", result);
                return result;
            }

        } catch (SQLException e) {
            log.error("❌ Ошибка получения последней даты", e);
            throw new RuntimeException("Ошибка получения последней даты: " + e.getMessage(), e);
        }

        return LocalDate.now();
    }
}
