package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.api.CandlesApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Сервис для экспорта исторических свечей в CSV
 */
public class CandlesExportService {
    private static final Logger log = LoggerFactory.getLogger(CandlesExportService.class);

    /**
     * Экспортирует исторические свечи в CSV файл
     *
     * @param figi     FIGI инструмента
     * @param from     Начало периода
     * @param to       Конец периода
     * @param interval Интервал свечей
     * @return Путь к созданному CSV файлу
     */
    public String exportCandlesToCsv(String figi, LocalDate from, LocalDate to, CandleInterval interval) {
        log.info("📥 Начало экспорта свечей: FIGI={}, период={} - {}, интервал={}",
                figi, from, to, interval.name());

        try {
            // 1. Получаем данные через API
            CandlesApiService apiService = new CandlesApiService(
                    com.algotrading.tinkoffinvestgui.config.ConnectorConfig.getApiToken(),
                    com.algotrading.tinkoffinvestgui.config.ConnectorConfig.API_URL,
                    com.algotrading.tinkoffinvestgui.config.ConnectorConfig.API_PORT
            );

            List<HistoricCandle> candles = apiService.getCandles(figi, from, to, interval);

            if (candles.isEmpty()) {
                log.warn("⚠️ Нет данных для экспорта");
                throw new RuntimeException("Нет данных для указанного периода");
            }

            // 2. Формируем имя файла
            String fileName = String.format("%s_%s-%s.csv",
                    figi,
                    from.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                    to.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            );

            // 3. Определяем путь к папке Downloads
            String downloadsPath = System.getProperty("user.home") + "\\Downloads\\" + fileName;

            // 4. Записываем CSV
            try (FileWriter writer = new FileWriter(downloadsPath)) {
                // Заголовок CSV
                writer.write("Date,Open,High,Low,Close,Volume\n");

                // Данные (используем Locale.US для точки как разделителя)
                for (HistoricCandle candle : candles) {
                    String date = formatTimestamp(candle.getTime());
                    double open = quotationToDouble(candle.getOpen());
                    double high = quotationToDouble(candle.getHigh());
                    double low = quotationToDouble(candle.getLow());
                    double close = quotationToDouble(candle.getClose());
                    long volume = candle.getVolume();

                    // Форматируем с точкой как разделитель (Locale.US)
                    writer.write(String.format(Locale.US, "%s,%.4f,%.4f,%.4f,%.4f,%d\n",
                            date, open, high, low, close, volume));
                }
            }

            log.info("✅ Экспорт завершён: {} свечей → {}", candles.size(), downloadsPath);
            return downloadsPath;

        } catch (IOException e) {
            log.error("❌ Ошибка записи CSV файла", e);
            throw new RuntimeException("Ошибка записи файла: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Ошибка экспорта свечей", e);
            throw new RuntimeException("Ошибка экспорта: " + e.getMessage(), e);
        }
    }

    /**
     * Конвертирует Protobuf Timestamp в строку даты (формат: YYYY-MM-DD)
     */
    private String formatTimestamp(com.google.protobuf.Timestamp timestamp) {
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        return DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.of("UTC"))
                .format(instant);
    }

    /**
     * Конвертирует Quotation (units + nano) в double
     */
    private double quotationToDouble(ru.tinkoff.piapi.contract.v1.Quotation quotation) {
        return quotation.getUnits() + quotation.getNano() / 1_000_000_000.0;
    }

    /**
     * Маппинг названия интервала в CandleInterval enum
     */
    public static CandleInterval getCandleInterval(String intervalName) {
        return switch (intervalName) {
            case "1 минута" -> CandleInterval.CANDLE_INTERVAL_1_MIN;
            case "5 минут" -> CandleInterval.CANDLE_INTERVAL_5_MIN;
            case "15 минут" -> CandleInterval.CANDLE_INTERVAL_15_MIN;
            case "1 час" -> CandleInterval.CANDLE_INTERVAL_HOUR;
            case "1 день" -> CandleInterval.CANDLE_INTERVAL_DAY;
            case "1 неделя" -> CandleInterval.CANDLE_INTERVAL_WEEK;
            case "1 месяц" -> CandleInterval.CANDLE_INTERVAL_MONTH;
            default -> throw new IllegalArgumentException("Неизвестный интервал: " + intervalName);
        };
    }
}
