package com.algotrading.tinkoffinvestgui.repository;

import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import ru.tinkoff.piapi.contract.v1.Bond;

import java.sql.*;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repository для экспорта облигаций в таблицу exportdata
 */
public class BondsRepository {

    private static final Logger log = LoggerFactory.getLogger(BondsRepository.class);
    private Connection getConnection() throws SQLException {
        String dbUrl = ConnectorConfig.getPropertyValue("db.url");
        String dbUser = ConnectorConfig.getPropertyValue("db.username");
        String dbPassword = ConnectorConfig.getPropertyValue("db.password");

        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Очищает таблицу exportdata
     */
    public void clearTable() {
        String sql = "TRUNCATE TABLE public.exportdata RESTART IDENTITY";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("✓ Таблица public.exportdata очищена");
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка очистки таблицы: " + e.getMessage(), e);
        }
    }

    /**
     * Вставляет первую строку с названиями полей (английские)
     */
    public void insertHeaderRow() {
        String sql = """
            INSERT INTO public.exportdata 
            (field01, field02, field03, field04, field05, field06, field07, field08, field09, field10, field11, field12, field13)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "figi");
            pstmt.setString(2, "ticker");
            pstmt.setString(3, "class_code");
            pstmt.setString(4, "isin");
            pstmt.setString(5, "name");
            pstmt.setString(6, "currency");
            pstmt.setString(7, "nominal_currency");
            pstmt.setString(8, "nominal");
            pstmt.setString(9, "maturity_date");
            pstmt.setString(10, "dlong_client");
            pstmt.setString(11, "floating_coupon_flag");
            pstmt.setString(12, "amortization_flag");
            pstmt.setString(13, "risk_level");

            pstmt.executeUpdate();
            log.info("✓ Заголовки полей добавлены");

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка вставки заголовков: " + e.getMessage(), e);
        }
    }

    /**
     * Экспортирует облигации в таблицу exportdata
     */
    public int exportBonds(List<Bond> bonds) {
        // 1. Очищаем таблицу
        clearTable();

        // 2. Вставляем заголовки
        insertHeaderRow();

        // 3. Вставляем данные облигаций
        String sql = """
            INSERT INTO public.exportdata 
            (field01, field02, field03, field04, field05, field06, field07, field08, field09, field10, field11, field12, field13)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        int count = 0;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (Bond bond : bonds) {
                // field01 - FIGI
                pstmt.setString(1, bond.getFigi());

                // field02 - Ticker
                pstmt.setString(2, bond.getTicker());

                // field03 - Class Code
                pstmt.setString(3, bond.getClassCode());

                // field04 - ISIN
                pstmt.setString(4, bond.getIsin());

                // field05 - Name
                pstmt.setString(5, bond.getName());

                // field06 - Currency
                pstmt.setString(6, bond.getCurrency().toUpperCase());

                // field07 - Nominal Currency
                if (bond.hasInitialNominal()) {
                    pstmt.setString(7, bond.getInitialNominal().getCurrency().toUpperCase());
                } else {
                    pstmt.setString(7, null);
                }

                // field08 - Nominal
                if (bond.hasInitialNominal()) {
                    double nominal = bond.getInitialNominal().getUnits() +
                            bond.getInitialNominal().getNano() / 1e9;
                    pstmt.setString(8, String.format("%.0f", nominal));
                } else {
                    pstmt.setString(8, null);
                }

                // field09 - Maturity Date
                if (bond.hasMaturityDate()) {
                    long seconds = bond.getMaturityDate().getSeconds();
                    java.time.LocalDate date = java.time.LocalDateTime
                            .ofEpochSecond(seconds, 0, java.time.ZoneOffset.UTC)
                            .toLocalDate();
                    pstmt.setString(9, date.toString());
                } else {
                    pstmt.setString(9, null);
                }

                // field10 - DlongClient
                if (bond.hasDlongMin()) {
                    double dlongClient = bond.getDlongMin().getUnits() +
                            bond.getDlongMin().getNano() / 1e9;
                    pstmt.setString(10, String.format("%.2f", dlongClient));
                } else {
                    pstmt.setString(10, "0");
                }

                // field11 - Floating Coupon Flag
                pstmt.setString(11, bond.getFloatingCouponFlag() ? "true" : "false");

                // field12 - Amortization Flag
                pstmt.setString(12, bond.getAmortizationFlag() ? "true" : "false");

                // field13 - Risk Level
                String riskLevel;
                switch (bond.getRiskLevel()) {
                    case RISK_LEVEL_LOW:
                        riskLevel = "LOW";
                        break;
                    case RISK_LEVEL_MODERATE:
                        riskLevel = "MODERATE";
                        break;
                    case RISK_LEVEL_HIGH:
                        riskLevel = "HIGH";
                        break;
                    default:
                        riskLevel = bond.getRiskLevel().name();
                }
                pstmt.setString(13, riskLevel);

                pstmt.executeUpdate();
                count++;
            }

            log.info("✓ Экспортировано облигаций: " + count);

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка экспорта облигаций: " + e.getMessage(), e);
        }

        return count;
    }

    /**
     * Получает количество записей в таблице
     */
    public int getRowCount() {
        String sql = "SELECT COUNT(*) FROM public.exportdata";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка подсчета строк: " + e.getMessage(), e);
        }

        return 0;
    }
}
