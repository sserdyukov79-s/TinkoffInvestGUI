# 📝 Изменения в BondStrategyBacktestService.java

## ❌ УДАЛИТЬ эти константы:

```java
// ❌ УДАЛИТЬ ВСЕ КОНСТАНТЫ:
private static final double VOLATILITY_MULTIPLIER = 1.2;
private static final double PROFIT_MARGIN = 0.008;
```

## ✅ ИЗМЕНИТЬ метод runBacktest:

```java
public BacktestReport runBacktest(LocalDate startDate, LocalDate endDate, BacktestFilters filters) throws Exception {
    log.info("🧪 Начало бэктестинга: {} - {} с фильтрами: валюта={}, без_амортизации={}",
            startDate, endDate, filters.currency, filters.withoutAmortization);
    
    // ✅ Загрузить ВСЕ параметры стратегии из БД
    ParametersRepository.StrategyParameters params = 
            parametersRepository.getStrategyParameters();
    
    log.info("📊 Параметры стратегии: множитель={}, коэфф_адаптивной_маржи={}, " +
            "мин_маржа={}%, макс_маржа={}%, комиссия={:.4f}%",
            params.volatilityMultiplier,
            params.volatilityCoefficient,
            params.minProfitMarginPercent,
            params.maxProfitMarginPercent,
            params.brokerCommission * 100);
    
    // Получить период анализа из БД
    int analysisPeriodMonths = getAnalysisPeriodMonths();
    log.info("📊 Период анализа волатильности: {} месяцев", analysisPeriodMonths);
    
    // Получить облигации с фильтрами
    List<BondInfo> bonds = loadBondsWithFilters(filters);
    log.info("📈 Загружено {} облигаций для бэктеста", bonds.size());
    
    if (bonds.isEmpty()) {
        throw new Exception("Нет облигаций для бэктестинга. Проверьте фильтры.");
    }
    
    // Для каждой облигации запустить бэктест
    List<BondBacktestResult> results = new ArrayList<>();
    int processed = 0;
    
    for (BondInfo bond : bonds) {
        processed++;
        if (processed % 10 == 0) {
            log.info("⏳ Обработано {}/{} облигаций ({}%)", processed, bonds.size(),
                    (processed * 100) / bonds.size());
        }
        
        try {
            // ✅ Передать параметры стратегии в backtestBond
            BondBacktestResult result = backtestBond(bond, startDate, endDate, 
                    analysisPeriodMonths, params);
            
            if (result.getTotalTrades() > 0) {
                results.add(result);
            }
        } catch (Exception e) {
            log.warn("⚠️ Ошибка бэктеста для {}: {}", bond.ticker, e.getMessage());
        }
    }
    
    // ... остальной код формирования отчёта без изменений
}
```

## ✅ ИЗМЕНИТЬ сигнатуру и логику метода backtestBond:

```java
/**
 * Бэктестинг для одной облигации с адаптивной маржой
 */
private BondBacktestResult backtestBond(BondInfo bond, LocalDate startDate, LocalDate endDate,
                                       int analysisPeriodMonths,
                                       ParametersRepository.StrategyParameters params) throws Exception {
    
    // Загрузить исторические данные
    LocalDate dataStart = startDate.minusMonths(analysisPeriodMonths);
    List<HistoricCandle> allCandles = candlesApiService.getCandles(
            bond.figi, dataStart, endDate, CandleInterval.CANDLE_INTERVAL_DAY);
    
    if (allCandles.isEmpty()) {
        log.warn("⚠️ Нет данных для {}", bond.ticker);
        return createEmptyResult(bond);
    }
    
    List<Trade> trades = new ArrayList<>();
    Trade currentTrade = null;
    
    // Симуляция торговли по дням
    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
        LocalDate finalDate = date;
        
        // Получить свечи за период анализа
        LocalDate analysisStart = date.minusMonths(analysisPeriodMonths);
        List<HistoricCandle> analysisPeriod = allCandles.stream()
                .filter(c -> {
                    LocalDate candleDate = LocalDate.ofEpochDay(c.getTime().getSeconds() / 86400);
                    return !candleDate.isBefore(analysisStart) && !candleDate.isAfter(finalDate);
                })
                .collect(Collectors.toList());
        
        if (analysisPeriod.isEmpty()) {
            continue;
        }
        
        // Рассчитать волатильность и цены
        double[] prices = analysisPeriod.stream()
                .mapToDouble(c -> c.getClose().getUnits() + c.getClose().getNano() / 1e9)
                .toArray();
        
        double volatility = calculateVolatility(prices);
        double avgPrice = calculateAverage(prices);
        double lastPrice = prices[prices.length - 1]; // Вчерашняя цена
        double volatilityPercent = (volatility / avgPrice) * 100;
        
        // ✅ ЦЕНА ПОКУПКИ (LAST_PRICE захардкожена)
        double buyPrice = lastPrice - (params.volatilityMultiplier * volatility);
        
        // ✅ КОМИССИЯ ПРИ ПОКУПКЕ
        double buyCommission = buyPrice * params.brokerCommission;
        
        // ✅ АДАПТИВНАЯ МАРЖА на основе волатильности
        double rawProfitMarginPercent = params.volatilityCoefficient * volatilityPercent;
        double profitMarginPercent = Math.max(params.minProfitMarginPercent, 
                                              Math.min(params.maxProfitMarginPercent, rawProfitMarginPercent));
        double profitMargin = profitMarginPercent / 100.0; // Перевод в десятичное
        
        // ✅ ЦЕНА ПРОДАЖИ с адаптивной маржой
        double targetProfit = buyPrice * profitMargin;
        double sellCommission = (buyPrice + targetProfit) * params.brokerCommission;
        double sellPrice = buyPrice + buyCommission + targetProfit + sellCommission;
        
        // Если нет позиции и текущая цена упала до buyPrice или ниже
        if (currentTrade == null && lastPrice <= buyPrice) {
            currentTrade = new Trade();
            currentTrade.setBuyDate(date);
            currentTrade.setBuyPrice(buyPrice);
            currentTrade.setVolatility(volatility);
            currentTrade.setBuyCommission(buyCommission);
            currentTrade.setTargetSellPrice(sellPrice);
            currentTrade.setAdaptiveMarginPercent(profitMarginPercent); // ✅ НОВОЕ ПОЛЕ
            
            log.debug("📥 {} Покупка по {:.2f}₽ (таргет {:.2f}₽, адаптивная маржа {:.2f}%, комиссия {:.2f}₽)",
                    bond.ticker, buyPrice, sellPrice, profitMarginPercent, buyCommission);
        }
        
        // Если есть позиция - проверяем условия продажи
        if (currentTrade != null) {
            int holdingDays = (int) (date.toEpochDay() - currentTrade.getBuyDate().toEpochDay());
            
            // ✅ Продажа если:
            // 1. Цена достигла таргета (sellPrice)
            // 2. Прошло 30 дней (выход по таймауту)
            boolean reachedTarget = lastPrice >= currentTrade.getTargetSellPrice();
            boolean timeout = holdingDays >= 30;
            
            if (reachedTarget || timeout) {
                double actualSellPrice = lastPrice;
                double actualSellCommission = actualSellPrice * params.brokerCommission;
                
                // ✅ ЧИСТАЯ ПРИБЫЛЬ с учётом ВСЕХ комиссий
                double totalCommissions = currentTrade.getBuyCommission() + actualSellCommission;
                double netProfit = (actualSellPrice - currentTrade.getBuyPrice()) - totalCommissions;
                double profitPercent = (netProfit / currentTrade.getBuyPrice()) * 100;
                
                currentTrade.setSellDate(date);
                currentTrade.setSellPrice(actualSellPrice);
                currentTrade.setSellCommission(actualSellCommission);
                currentTrade.setHoldingDays(holdingDays);
                currentTrade.setProfit(netProfit);
                currentTrade.setProfitPercent(profitPercent);
                
                trades.add(currentTrade);
                
                String reason = reachedTarget ? "таргет" : "таймаут";
                log.debug("📤 {} Продажа по {:.2f}₽ (прибыль {:.2f}₽/{:.2f}%, " +
                        "адаптивная маржа {:.2f}%, комиссии {:.2f}₽, причина: {})",
                        bond.ticker, actualSellPrice, netProfit, profitPercent, 
                        currentTrade.getAdaptiveMarginPercent(), totalCommissions, reason);
                
                currentTrade = null;
            }
        }
    }
    
    // Закрыть открытую позицию в конце периода
    if (currentTrade != null) {
        double[] lastPrices = allCandles.stream()
                .mapToDouble(c -> c.getClose().getUnits() + c.getClose().getNano() / 1e9)
                .toArray();
        double lastPrice = lastPrices[lastPrices.length - 1];
        
        int holdingDays = (int) (endDate.toEpochDay() - currentTrade.getBuyDate().toEpochDay());
        double actualSellCommission = lastPrice * params.brokerCommission;
        double totalCommissions = currentTrade.getBuyCommission() + actualSellCommission;
        double netProfit = (lastPrice - currentTrade.getBuyPrice()) - totalCommissions;
        double profitPercent = (netProfit / currentTrade.getBuyPrice()) * 100;
        
        currentTrade.setSellDate(endDate);
        currentTrade.setSellPrice(lastPrice);
        currentTrade.setSellCommission(actualSellCommission);
        currentTrade.setHoldingDays(holdingDays);
        currentTrade.setProfit(netProfit);
        currentTrade.setProfitPercent(profitPercent);
        
        trades.add(currentTrade);
        
        log.debug("📤 {} Закрытие позиции в конце периода по {:.2f}₽ " +
                "(прибыль {:.2f}₽/{:.2f}%, адаптивная маржа {:.2f}%)",
                bond.ticker, lastPrice, netProfit, profitPercent, 
                currentTrade.getAdaptiveMarginPercent());
    }
    
    // Сформировать результат (без изменений)
    BondBacktestResult result = new BondBacktestResult();
    result.setTicker(bond.ticker);
    result.setName(bond.name);
    result.setTrades(trades);
    
    int profitable = (int) trades.stream().filter(t -> t.getProfit() > 0).count();
    int losing = trades.size() - profitable;
    double totalProfit = trades.stream().mapToDouble(Trade::getProfit).sum();
    double avgProfit = trades.isEmpty() ? 0 : totalProfit / trades.size();
    double avgProfitPercent = trades.stream().mapToDouble(Trade::getProfitPercent).average().orElse(0);
    double winRate = trades.isEmpty() ? 0 : (profitable * 100.0 / trades.size());
    double avgHolding = trades.stream().mapToInt(Trade::getHoldingDays).average().orElse(0);
    
    result.setTotalTrades(trades.size());
    result.setProfitableTrades(profitable);
    result.setLosingTrades(losing);
    result.setTotalProfit(totalProfit);
    result.setAvgProfit(avgProfit);
    result.setAvgProfitPercent(avgProfitPercent);
    result.setWinRate(winRate);
    result.setAvgHoldingDays(avgHolding);
    
    return result;
}
```

## ✅ ДОБАВИТЬ новое поле в класс Trade:

```java
public static class Trade {
    private LocalDate buyDate;
    private double buyPrice;
    private double volatility;
    private double buyCommission;
    private double targetSellPrice;
    private double adaptiveMarginPercent; // ✅ НОВОЕ ПОЛЕ
    
    private LocalDate sellDate;
    private double sellPrice;
    private double sellCommission;
    private int holdingDays;
    private double profit;
    private double profitPercent;
    
    // Getters and Setters
    public double getAdaptiveMarginPercent() {
        return adaptiveMarginPercent;
    }
    
    public void setAdaptiveMarginPercent(double adaptiveMarginPercent) {
        this.adaptiveMarginPercent = adaptiveMarginPercent;
    }
    
    // ... остальные getters/setters без изменений
}
```

---

## 📊 Итоговые изменения

### Удалено:
- ❌ `private static final double VOLATILITY_MULTIPLIER = 1.2;`
- ❌ `private static final double PROFIT_MARGIN = 0.008;`

### Изменено:
- ✅ `runBacktest()` - загрузка `StrategyParameters` вместо только комиссии
- ✅ `backtestBond()` - принимает `StrategyParameters` вместо `brokerCommission`
- ✅ Расчёт адаптивной маржи на основе волатильности каждой облигации
- ✅ Логирование включает адаптивную маржу

### Добавлено:
- ✅ Поле `adaptiveMarginPercent` в класс `Trade`
- ✅ Getter/Setter для `adaptiveMarginPercent`
- ✅ Расчёт адаптивной маржи в цикле бэктеста
- ✅ Логирование адаптивной маржи при покупке/продаже

---

## 🎯 Результат

Теперь бэктест использует **ту же самую адаптивную маржу**, что и анализ в `BondStrategyCalculator`!

**Формула адаптивной маржи:**
```
rawMargin = VOLATILITY_COEFFICIENT × volatilityPercent
profitMargin = clamp(rawMargin, MIN_PROFIT_MARGIN_PERCENT, MAX_PROFIT_MARGIN_PERCENT)
```

**Пример:** При волатильности 2% и коэффициенте 0.4:
- Расчёт: 0.4 × 2% = 0.8%
- Итоговая маржа: 0.8% (в пределах 0.3%-2%)
