package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.db.DatabaseConnection;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.repository.InstrumentsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/**
 * Сервис ежедневной подготовки данных:
 * 1. Копирование инструментов на новую дату
 * 2. Расчёт цен покупки/продажи по алгоритму
 * 3. Обновление buy_price/sell_price в БД
 */
public class DailyDataPreparationService {

    private static final Logger log = LoggerFactory.getLogger(DailyDataPreparationService.class);

    private final InstrumentsRepository instrumentsRepository;
    private final BondPriceCalculator priceCalculator;

    public DailyDataPreparationService(InstrumentsRepository instrumentsRepository) {
        this.instrumentsRepository = instrumentsRepository;
        this.priceCalculator = new BondPriceCalculator();
    }

    /**
     * Выполняет полный цикл подготовки данных на новый день:
     * 1. Копирование записей с предыдущего дня
     * 2. Расчёт цен по алгоритму бэктестинга
     * 3. Обновление buy_price/sell_price
     *
     * @return true если подготовка выполнена успешно
     */
    public boolean prepareDailyData() {
        log.info("═══════════════════════════════════════════════════════");
        log.info("🔄 Начало ежедневной подготовки данных");
        log.info("═══════════════════════════════════════════════════════");

        try {
            // Шаг 1: Копирование инструментов на текущую дату
            boolean copied = copyInstrumentsToCurrentDate();
            if (!copied) {
                log.info("ℹ️ Инструменты уже скопированы на сегодня, пропускаем");
                // Продолжаем, т.к. может быть нужен пересчёт цен
            }

            // Шаг 2: Расчёт и обновление цен
            int updatedCount = recalculateAndUpdatePrices();

            log.info("═══════════════════════════════════════════════════════");
            log.info("✅ Подготовка данных завершена успешно");
            log.info("   Обновлено инструментов: {}", updatedCount);
            log.info("═══════════════════════════════════════════════════════");
            return true;
        } catch (Exception e) {
            log.error("❌ Ошибка при подготовке данных", e);
            return false;
        }
    }

    /**
     * Шаг 1: Копирует инструменты с последней даты на CURRENT_DATE
     */
    private boolean copyInstrumentsToCurrentDate() {
        log.info("📋 Шаг 1: Копирование инструментов на текущую дату");

        String sql =
                "WITH last_date AS ( " +
                        "  SELECT MAX(bookdate) AS max_date " +
                        "  FROM public.instruments " +
                        ") " +
                        "INSERT INTO public.instruments( " +
                        "  bookdate, figi, name, isin, priority, " +
                        "  buy_price, buy_quantity, sell_price, sell_quantity, " +
                        "  manual_buy_price, manual_sell_price, " +
                        "  sell_price_fixed, sell_price_fixed_date " +
                        ") " +
                        "SELECT DISTINCT ON (i.id) " +
                        "  CURRENT_DATE AS bookdate, " +
                        "  i.figi, i.name, i.isin, i.priority, " +
                        "  i.buy_price, i.buy_quantity, " +
                        "  i.sell_price, i.sell_quantity, " +
                        "  i.manual_buy_price, i.manual_sell_price, " +
                        "  i.sell_price_fixed, i.sell_price_fixed_date " +
                        "FROM public.instruments i " +
                        "JOIN last_date ld ON i.bookdate = ld.max_date " +
                        "  AND (i.buy_quantity IS NOT NULL OR i.sell_quantity IS NOT NULL) " +
                        "WHERE NOT EXISTS ( " +
                        "  SELECT 1 " +
                        "  FROM public.instruments i2 " +
                        "  WHERE i2.bookdate = CURRENT_DATE " +
                        "    AND i2.figi = i.figi " +
                        "    AND i2.priority = i.priority " +
                        ")";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            int rowsInserted = stmt.executeUpdate(sql);
            if (rowsInserted > 0) {
                log.info("✅ Скопировано инструментов: {}", rowsInserted);
                return true;
            } else {
                log.info("ℹ️ Записи на текущую дату уже существуют");
                return false;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при копировании инструментов", e);
            throw new RuntimeException("Ошибка копирования инструментов", e);
        }
    }

    /**
     * Шаг 2: Рассчитывает цены по алгоритму бэктестинга и обновляет БД
     */
    private int recalculateAndUpdatePrices() {
        log.info("💰 Шаг 2: Расчёт цен по алгоритму бэктестинга");

        // Получаем все инструменты на текущую дату
        List<Instrument> instruments = instrumentsRepository.findAll();
        if (instruments.isEmpty()) {
            log.warn("⚠️ Нет инструментов для расчёта цен");
            return 0;
        }

        log.info("📊 Найдено инструментов: {}", instruments.size());

        int updatedCount = 0;
        int skippedCount = 0;

        for (Instrument instrument : instruments) {
            try {
                // при желании можно снова включить skip по manual_* ценам

                PriceCalculationResult result = priceCalculator.calculatePrices(instrument);
                if (result.isSuccess()) {
                    instrument.setBuyPrice(result.getBuyPrice());
                    instrument.setSellPrice(result.getSellPrice());
                    // sell_price_fixed* НЕ трогаем
                    instrumentsRepository.update(instrument);

                    log.info("✅ Обновлены цены '{}': buy={}, sell={}",
                            instrument.getName(),
                            result.getBuyPrice(),
                            result.getSellPrice());
                    updatedCount++;
                } else {
                    log.warn("⚠️ Не удалось рассчитать цены для '{}': {}",
                            instrument.getName(), result.getErrorMessage());
                    skippedCount++;
                }
            } catch (Exception e) {
                log.error("❌ Ошибка при обработке инструмента '{}'", instrument.getName(), e);
                skippedCount++;
            }
        }

        log.info("📈 Результат расчёта цен:");
        log.info("   ✅ Обновлено: {}", updatedCount);
        log.info("   ⏭️ Пропущено: {}", skippedCount);

        return updatedCount;
    }

    /**
     * Обёртка результата (оставлена как в твоём классе, если используешь)
     */
    public static class PriceCalculationResult {
        private final boolean success;
        private final BigDecimal buyPrice;
        private final BigDecimal sellPrice;
        private final String errorMessage;

        public static PriceCalculationResult success(BigDecimal buyPrice, BigDecimal sellPrice) {
            return new PriceCalculationResult(true, buyPrice, sellPrice, null);
        }

        public static PriceCalculationResult failure(String errorMessage) {
            return new PriceCalculationResult(false, null, null, errorMessage);
        }

        private PriceCalculationResult(boolean success, BigDecimal buyPrice,
                                       BigDecimal sellPrice, String errorMessage) {
            this.success = success;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public BigDecimal getBuyPrice() { return buyPrice; }
        public BigDecimal getSellPrice() { return sellPrice; }
        public String getErrorMessage() { return errorMessage; }
    }
}
