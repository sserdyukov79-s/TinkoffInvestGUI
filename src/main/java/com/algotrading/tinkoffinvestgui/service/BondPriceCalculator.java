package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.api.CandlesApiService;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.repository.ParametersRepository;
import com.algotrading.tinkoffinvestgui.service.DailyDataPreparationService.PriceCalculationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Калькулятор цен покупки/продажи по алгоритму бэктестинга облигаций
 */
public class BondPriceCalculator {
    private static final Logger log = LoggerFactory.getLogger(BondPriceCalculator.class);

    private final CandlesApiService candlesService;
    private final ParametersRepository parametersRepository;

    // Параметры алгоритма (из parameters таблицы)
    private double volatilityMultiplier;
    private double brokerCommissionDecimal;

    public BondPriceCalculator() {
        this.candlesService = new CandlesApiService(
                ConnectorConfig.getApiToken(),
                ConnectorConfig.API_URL,
                ConnectorConfig.API_PORT
        );
        this.parametersRepository = new ParametersRepository();
        loadParameters();
    }

    /**
     * Загружает параметры из БД (как в BondStrategyBacktestService)
     */
    private void loadParameters() {
        try {
            // Множитель волатильности (default 1.2)
            String volatilityStr = parametersRepository.getParameterValue("VOLATILITY_MULTIPLIER");
            this.volatilityMultiplier = (volatilityStr != null && !volatilityStr.isEmpty())
                    ? Double.parseDouble(volatilityStr)
                    : 1.2;

            // Комиссия брокера (default 0.04%)
            String commissionStr = parametersRepository.getParameterValue("BROKER_COMMISSION_PERCENT");
            double commissionPercent = (commissionStr != null && !commissionStr.isEmpty())
                    ? Double.parseDouble(commissionStr)
                    : 0.04;
            this.brokerCommissionDecimal = commissionPercent / 100.0;

            log.debug("Параметры алгоритма: volatilityMultiplier={}, commissionPercent={}%",
                    volatilityMultiplier, commissionPercent);

        } catch (Exception e) {
            log.warn("Не удалось загрузить параметры, используем значения по умолчанию", e);
            this.volatilityMultiplier = 1.2;
            this.brokerCommissionDecimal = 0.0004; // 0.04%
        }
    }

    /**
     * Рассчитывает цены покупки и продажи для инструмента
     */
    public PriceCalculationResult calculatePrices(Instrument instrument) {
        try {
            // Проверка FIGI
            if (instrument.getFigi() == null || instrument.getFigi().isEmpty()) {
                return PriceCalculationResult.failure("FIGI отсутствует");
            }

            // Получаем исторические данные (последние 30 дней для расчёта волатильности)
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(30);

            List<HistoricCandle> candles = candlesService.getCandles(
                    instrument.getFigi(),
                    startDate,
                    endDate,
                    CandleInterval.CANDLE_INTERVAL_DAY
            );

            if (candles.isEmpty()) {
                return PriceCalculationResult.failure("Нет исторических данных");
            }

            // Последняя цена закрытия
            HistoricCandle lastCandle = candles.get(candles.size() - 1);
            double lastPrice = quotationToDouble(lastCandle.getClose());

            if (lastPrice <= 0) {
                return PriceCalculationResult.failure("Некорректная последняя цена");
            }

            // Расчёт волатильности (стандартное отклонение цен закрытия)
            double volatility = calculateVolatility(candles);

            // Расчёт цены покупки: lastPrice - (volatilityMultiplier * volatility)
            double buyPriceRaw = lastPrice - (volatilityMultiplier * volatility);

            // Расчёт цены продажи: lastPrice
            double sellPriceRaw = calculateSellPrice(lastPrice, buyPriceRaw);

            // ✅ Умножение на 10 (цена в деньгах, а не процентах) + округление
            BigDecimal buyPrice = BigDecimal.valueOf(buyPriceRaw)
                    .multiply(BigDecimal.TEN)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal sellPrice = BigDecimal.valueOf(sellPriceRaw)
                    .multiply(BigDecimal.TEN)
                    .setScale(2, RoundingMode.HALF_UP);

            // Проверка корректности
            if (buyPrice.compareTo(BigDecimal.ZERO) <= 0 ||
                    sellPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return PriceCalculationResult.failure("Рассчитанные цены некорректны");
            }

            log.debug("Расчёт для '{}': lastPrice={}, volatility={}, buyPrice={}, sellPrice={}",
                    instrument.getName(), lastPrice, volatility, buyPrice, sellPrice);

            return PriceCalculationResult.success(buyPrice, sellPrice);

        } catch (Exception e) {
            log.error("Ошибка расчёта цен для '{}'", instrument.getName(), e);
            return PriceCalculationResult.failure(e.getMessage());
        }
    }

    /**
     * Рассчитывает волатильность (стандартное отклонение)
     */
    private double calculateVolatility(List<HistoricCandle> candles) {
        if (candles.size() < 2) {
            return 0.0;
        }

        // Средняя цена
        double sum = 0.0;
        for (HistoricCandle candle : candles) {
            sum += quotationToDouble(candle.getClose());
        }
        double mean = sum / candles.size();

        // Стандартное отклонение
        double varianceSum = 0.0;
        for (HistoricCandle candle : candles) {
            double price = quotationToDouble(candle.getClose());
            varianceSum += Math.pow(price - mean, 2);
        }

        double variance = varianceSum / candles.size();
        return Math.sqrt(variance);
    }

    /**
     * Рассчитывает цену продажи с учётом комиссий
     * (чтобы после вычета комиссий получить прибыль)
     */
    private double calculateSellPrice(double lastPrice, double buyPrice) {
        // Комиссия за покупку
        double buyCommission = buyPrice * brokerCommissionDecimal;

        // Минимальная цена продажи для безубытка:
        // sellPrice * (1 - commission) >= buyPrice + buyCommission
        double minSellPrice = (buyPrice + buyCommission) / (1.0 - brokerCommissionDecimal);

        // Продаём по текущей цене, если она выше минимальной
        return Math.max(lastPrice, minSellPrice);
    }

    /**
     * Конвертирует Quotation в double
     */
    private double quotationToDouble(ru.tinkoff.piapi.contract.v1.Quotation quotation) {
        if (quotation == null) {
            return 0.0;
        }
        return quotation.getUnits() + (quotation.getNano() / 1_000_000_000.0);
    }
}
