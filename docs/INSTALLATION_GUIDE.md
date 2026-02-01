# 🚀 Инструкция по внедрению адаптивной маржи

## 📋 Обзор

Переделываем стратегию "Ловец дна" с **фиксированной маржи (0.8%)** на **адаптивную маржу**, которая зависит от волатильности каждой облигации.

### Формула адаптивной маржи:

```
profitMargin = VOLATILITY_COEFFICIENT × volatilityPercent
```

С ограничениями:
```
MIN_PROFIT_MARGIN_PERCENT ≤ profitMargin ≤ MAX_PROFIT_MARGIN_PERCENT
```

### Пример:

| Волатильность | Коэфф | Расчёт | Лимиты [0.3%, 2%] | Итоговая маржа |
|--------------|-------|--------|-------------------|----------------|
| 0.5% | 0.4 | 0.4×0.5 = 0.2% | 0.2% < мин | **0.3%** (мин) |
| 1% | 0.4 | 0.4×1 = 0.4% | 0.3% ≤ 0.4% ≤ 2% | **0.4%** ✅ |
| 2% | 0.4 | 0.4×2 = 0.8% | 0.3% ≤ 0.8% ≤ 2% | **0.8%** ✅ |
| 5% | 0.4 | 0.4×5 = 2% | 0.3% ≤ 2% ≤ 2% | **2%** ✅ |
| 10% | 0.4 | 0.4×10 = 4% | 4% > макс | **2%** (макс) |

---

## ШАГ 1: Выполнить SQL-скрипт

### 1.1 Добавить параметры в БД

Выполните файл **strategy_params_adaptive.sql**:

```bash
psql -U your_user -d your_database -f strategy_params_adaptive.sql
```

Или вручную:

```sql
-- Параметры адаптивной маржи
INSERT INTO parameters (parameter, value) VALUES ('BROKER_COMMISSION_PERCENT', '0.04') ON CONFLICT (parameter) DO UPDATE SET value = EXCLUDED.value;
INSERT INTO parameters (parameter, value) VALUES ('VOLATILITY_MULTIPLIER', '1.2') ON CONFLICT (parameter) DO UPDATE SET value = EXCLUDED.value;
INSERT INTO parameters (parameter, value) VALUES ('VOLATILITY_COEFFICIENT', '0.4') ON CONFLICT (parameter) DO UPDATE SET value = EXCLUDED.value;
INSERT INTO parameters (parameter, value) VALUES ('MIN_PROFIT_MARGIN_PERCENT', '0.3') ON CONFLICT (parameter) DO UPDATE SET value = EXCLUDED.value;
INSERT INTO parameters (parameter, value) VALUES ('MAX_PROFIT_MARGIN_PERCENT', '2') ON CONFLICT (parameter) DO UPDATE SET value = EXCLUDED.value;
```

### 1.2 Проверить результат

```sql
SELECT parameter, value 
FROM parameters 
WHERE parameter IN (
    'BROKER_COMMISSION_PERCENT',
    'VOLATILITY_MULTIPLIER',
    'VOLATILITY_COEFFICIENT',
    'MIN_PROFIT_MARGIN_PERCENT',
    'MAX_PROFIT_MARGIN_PERCENT'
)
ORDER BY parameter;
```

Ожидаемый результат:
```
┌────────────────────────────────┬────────┐
│ parameter                      │ value  │
├────────────────────────────────┼────────┤
│ BROKER_COMMISSION_PERCENT      │ 0.04   │
│ MAX_PROFIT_MARGIN_PERCENT      │ 2      │
│ MIN_PROFIT_MARGIN_PERCENT      │ 0.3    │
│ VOLATILITY_COEFFICIENT         │ 0.4    │
│ VOLATILITY_MULTIPLIER          │ 1.2    │
└────────────────────────────────┴────────┘
```

### 1.3 Удалить старые параметры (опционально)

```sql
-- Удалить старые неиспользуемые параметры
DELETE FROM parameters WHERE parameter = 'PROFIT_MARGIN';
DELETE FROM parameters WHERE parameter = 'PRICE_BASE';
DELETE FROM parameters WHERE parameter = 'AVG_PRICE_VOLATILITY_MULTIPLIER';
```

---

## ШАГ 2: Обновить ParametersRepository.java

Замените файл на **ParametersRepository-updated.java**

### Ключевые изменения:

#### 2.1 Новые методы для параметров:

```java
public double getVolatilityMultiplier()        // VOLATILITY_MULTIPLIER
public double getVolatilityCoefficient()       // VOLATILITY_COEFFICIENT
public double getMinProfitMarginPercent()      // MIN_PROFIT_MARGIN_PERCENT
public double getMaxProfitMarginPercent()      // MAX_PROFIT_MARGIN_PERCENT
```

#### 2.2 Обновлённый StrategyParameters:

```java
public static class StrategyParameters {
    public double brokerCommission;           // 0.0004 (0.04%)
    public double volatilityMultiplier;       // 1.2
    public double volatilityCoefficient;      // 0.4
    public double minProfitMarginPercent;     // 0.3%
    public double maxProfitMarginPercent;     // 2%
}
```

---

## ШАГ 3: Обновить BondStrategyCalculator.java

Замените файл на **BondStrategyCalculator-updated.java**

### Ключевые изменения:

#### 3.1 Удалить константы:

```java
// ❌ УДАЛИТЬ ЭТИ СТРОКИ:
private static final double VOLATILITY_MULTIPLIER = 1.2;
private static final double PROFIT_MARGIN = 0.008;
```

#### 3.2 Добавить константу PRICE_BASE:

```java
// ✅ PRICE_BASE захардкожена как LAST_PRICE
private static final String PRICE_BASE = "LAST_PRICE";
```

#### 3.3 Обновить метод calculatePrices:

```java
public static StrategyRecommendation calculatePrices(
        BondsAnalysisService.BondAnalysisResult analysis,
        ParametersRepository.StrategyParameters params) {
    
    // ... расчёт lastPrice, volatility, avgPrice
    
    // ✅ ЦЕНА ПОКУПКИ (LAST_PRICE захардкожена)
    double buyPrice = lastPrice - (params.volatilityMultiplier * volatility);
    
    // ✅ АДАПТИВНАЯ МАРЖА
    double rawProfitMarginPercent = params.volatilityCoefficient * volatilityPercent;
    double profitMarginPercent = Math.max(params.minProfitMarginPercent, 
                                          Math.min(params.maxProfitMarginPercent, rawProfitMarginPercent));
    double profitMargin = profitMarginPercent / 100.0;
    
    // ✅ ЦЕНА ПРОДАЖИ с адаптивной маржой
    double targetProfit = buyPrice * profitMargin;
    double sellCommission = (buyPrice + targetProfit) * params.brokerCommission;
    double sellPrice = buyPrice + buyCommission + targetProfit + sellCommission;
    
    // ... остальная логика
}
```

#### 3.4 Добавить поле в StrategyRecommendation:

```java
private double adaptiveMarginPercent; // ✅ НОВОЕ ПОЛЕ

public double getAdaptiveMarginPercent() {
    return adaptiveMarginPercent;
}

public void setAdaptiveMarginPercent(double adaptiveMarginPercent) {
    this.adaptiveMarginPercent = adaptiveMarginPercent;
}
```

---

## ШАГ 4: Обновить BondStrategyBacktestService.java

Используйте инструкции из файла **BondStrategyBacktest-changes.md**

### Ключевые изменения:

#### 4.1 Удалить константы:

```java
// ❌ УДАЛИТЬ:
private static final double VOLATILITY_MULTIPLIER = 1.2;
private static final double PROFIT_MARGIN = 0.008;
```

#### 4.2 Изменить runBacktest():

```java
public BacktestReport runBacktest(...) throws Exception {
    // ✅ Загрузить ВСЕ параметры стратегии
    ParametersRepository.StrategyParameters params = 
            parametersRepository.getStrategyParameters();
    
    log.info("📊 Параметры: множитель={}, коэфф={}, мин={}%, макс={}%",
            params.volatilityMultiplier,
            params.volatilityCoefficient,
            params.minProfitMarginPercent,
            params.maxProfitMarginPercent);
    
    // Передать params в backtestBond
    BondBacktestResult result = backtestBond(bond, startDate, endDate, 
            analysisPeriodMonths, params);
}
```

#### 4.3 Изменить backtestBond():

```java
private BondBacktestResult backtestBond(BondInfo bond, LocalDate startDate, LocalDate endDate,
                                       int analysisPeriodMonths,
                                       ParametersRepository.StrategyParameters params) throws Exception {
    
    // ... цикл по дням
    
    // ✅ АДАПТИВНАЯ МАРЖА
    double volatilityPercent = (volatility / avgPrice) * 100;
    double rawProfitMarginPercent = params.volatilityCoefficient * volatilityPercent;
    double profitMarginPercent = Math.max(params.minProfitMarginPercent, 
                                          Math.min(params.maxProfitMarginPercent, rawProfitMarginPercent));
    double profitMargin = profitMarginPercent / 100.0;
    
    // ✅ ЦЕНА ПРОДАЖИ с адаптивной маржой
    double targetProfit = buyPrice * profitMargin;
    double sellCommission = (buyPrice + targetProfit) * params.brokerCommission;
    double sellPrice = buyPrice + buyCommission + targetProfit + sellCommission;
    
    // Сохранить адаптивную маржу в трейде
    currentTrade.setAdaptiveMarginPercent(profitMarginPercent);
}
```

#### 4.4 Добавить поле в Trade:

```java
public static class Trade {
    // ... существующие поля
    private double adaptiveMarginPercent; // ✅ НОВОЕ
    
    public double getAdaptiveMarginPercent() {
        return adaptiveMarginPercent;
    }
    
    public void setAdaptiveMarginPercent(double adaptiveMarginPercent) {
        this.adaptiveMarginPercent = adaptiveMarginPercent;
    }
}
```

---

## ШАГ 5: Обновить TinkoffInvestGui.java (если используется)

### 5.1 Найти метод showAnalysisResults():

```java
private void showAnalysisResults(List<BondsAnalysisService.BondAnalysisResult> results) {
    log.info("📊 Отображение результатов анализа: {} облигаций", results.size());
    
    // ✅ Загрузить параметры стратегии из БД
    ParametersRepository paramsRepo = new ParametersRepository();
    ParametersRepository.StrategyParameters strategyParams = 
            paramsRepo.getStrategyParameters();
    
    // Создать таблицу
    Object[][] data = new Object[results.size()][columns.length];
    for (int i = 0; i < results.size(); i++) {
        BondsAnalysisService.BondAnalysisResult r = results.get(i);
        
        // ✅ Использовать параметры из БД
        BondStrategyCalculator.StrategyRecommendation strategy = 
                BondStrategyCalculator.calculatePrices(r, strategyParams);
        
        // ... заполнение таблицы
    }
    
    // Listener для выбора строки
    final ParametersRepository.StrategyParameters finalParams = strategyParams;
    table.getSelectionModel().addListSelectionListener(e -> {
        if (!e.getValueIsAdjusting()) {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                BondsAnalysisService.BondAnalysisResult analysis = results.get(selectedRow);
                BondStrategyCalculator.StrategyRecommendation strategy = 
                        BondStrategyCalculator.calculatePrices(analysis, finalParams);
                showStrategyDetails(analysis, strategy);
            }
        }
    });
}
```

---

## ШАГ 6: Пересобрать проект

```bash
# Очистить и пересобрать
mvn clean compile

# Запустить тесты (если есть)
mvn test

# Упаковать
mvn package
```

---

## ШАГ 7: Проверка работы

### 7.1 Проверить логи при запуске:

Должны появиться строки:
```
📊 Загружены параметры стратегии: комиссия=0.0400%, множитель_волатильности=1.2, 
   коэфф_адаптивной_маржи=0.4, мин_маржа=0.3%, макс_маржа=2%
```

### 7.2 Запустить анализ облигаций:

В GUI или через API, проверить что:
- ✅ Цены покупки/продажи рассчитываются
- ✅ В логах видна адаптивная маржа для каждой облигации
- ✅ Маржа разная для облигаций с разной волатильностью

### 7.3 Запустить бэктест:

```java
// Пример запуска бэктеста
LocalDate startDate = LocalDate.of(2024, 1, 1);
LocalDate endDate = LocalDate.of(2024, 12, 31);
BondStrategyBacktestService.BacktestFilters filters = new BondStrategyBacktestService.BacktestFilters();

BacktestReport report = backtestService.runBacktest(startDate, endDate, filters);

System.out.println("Винрейт: " + report.getWinRate() + "%");
System.out.println("Общая прибыль: " + report.getTotalProfit() + "₽");
```

В логах должны быть строки:
```
📊 Параметры стратегии: множитель=1.2, коэфф_адаптивной_маржи=0.4, мин_маржа=0.3%, макс_маржа=2%
📥 RU000ABC1234 Покупка по 1000.00₽ (таргет 1008.50₽, адаптивная маржа 0.80%, комиссия 0.40₽)
📤 RU000ABC1234 Продажа по 1009.00₽ (прибыль 8.10₽/0.81%, адаптивная маржа 0.80%, комиссии 0.80₽, причина: таргет)
```

---

## ШАГ 8: Настройка параметров

### 8.1 Изменить параметры в БД:

```sql
-- Более агрессивная стратегия (больше маржа при высокой волатильности)
UPDATE parameters SET value = '0.5' WHERE parameter = 'VOLATILITY_COEFFICIENT';
UPDATE parameters SET value = '0.5' WHERE parameter = 'MIN_PROFIT_MARGIN_PERCENT';
UPDATE parameters SET value = '3' WHERE parameter = 'MAX_PROFIT_MARGIN_PERCENT';

-- Более консервативная стратегия (меньше маржа)
UPDATE parameters SET value = '0.3' WHERE parameter = 'VOLATILITY_COEFFICIENT';
UPDATE parameters SET value = '0.2' WHERE parameter = 'MIN_PROFIT_MARGIN_PERCENT';
UPDATE parameters SET value = '1.5' WHERE parameter = 'MAX_PROFIT_MARGIN_PERCENT';
```

### 8.2 Сравнить результаты:

Запустите бэктест с разными параметрами и сравните:
- Количество сделок
- Винрейт
- Общую прибыль
- Среднюю прибыль на сделку

---

## 🎯 Готово!

Теперь стратегия использует **адаптивную маржу**, которая:
- ✅ Автоматически подстраивается под волатильность каждой облигации
- ✅ Компенсирует риск (высокая волатильность → выше маржа)
- ✅ Ускоряет выходы (низкая волатильность → ниже маржа)
- ✅ Защищена лимитами (MIN и MAX)
- ✅ Настраивается через БД без перекомпиляции

**Преимущества:**
1. Больше гибкости - каждая облигация с оптимальной маржой
2. Лучший risk/reward - компенсация за риск
3. Быстрее оборачиваемость - стабильные бумаги быстрее продаются
4. Проще тестирование - меняй коэффициент в БД и перезапускай

**Следующий шаг:** Запустите A/B тесты с разными значениями `VOLATILITY_COEFFICIENT` (0.3, 0.4, 0.5) и выберите оптимальное!
