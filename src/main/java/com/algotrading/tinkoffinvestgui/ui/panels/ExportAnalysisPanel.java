package com.algotrading.tinkoffinvestgui.ui.panels;

import ru.tinkoff.piapi.contract.v1.BondsResponse;
import com.algotrading.tinkoffinvestgui.api.BondsService;
import com.algotrading.tinkoffinvestgui.api.CandlesApiService;
import com.algotrading.tinkoffinvestgui.repository.BondsRepository;
import com.algotrading.tinkoffinvestgui.repository.ParametersRepository;
import com.algotrading.tinkoffinvestgui.service.BondsAnalysisService;
import com.algotrading.tinkoffinvestgui.service.BondStrategyBacktestService;
import com.algotrading.tinkoffinvestgui.ui.utils.AsyncTask;
import com.algotrading.tinkoffinvestgui.ui.utils.DialogUtils;
import com.algotrading.tinkoffinvestgui.config.ConnectorConfig;
import com.algotrading.tinkoffinvestgui.ui.dialogs.BondsAnalysisDialog;
import com.algotrading.tinkoffinvestgui.ui.dialogs.BacktestDialog;
import com.algotrading.tinkoffinvestgui.ui.dialogs.CandlesExportDialog;
import com.algotrading.tinkoffinvestgui.ui.dialogs.AnalysisResultsDialog;
import com.algotrading.tinkoffinvestgui.ui.dialogs.BacktestReportDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ExportAnalysisPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ExportAnalysisPanel.class);

    private final JFrame parentFrame;
    private JButton bondsButton;

    public ExportAnalysisPanel(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Экспорт и анализ данных Tinkoff Invest", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        add(title, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        // 1. Экспорт облигаций в БД
        JPanel bondsSection = createBondsSection();
        centerPanel.add(bondsSection);
        centerPanel.add(Box.createVerticalStrut(20));

        // 2. Анализ облигаций
        JPanel analysisSection = createAnalysisSection();
        centerPanel.add(analysisSection);
        centerPanel.add(Box.createVerticalStrut(20));

        // 3. Бэктест стратегии
        JPanel backtestSection = createBacktestSection();
        centerPanel.add(backtestSection);
        centerPanel.add(Box.createVerticalStrut(20));

        // 4. Экспорт свечей
        JPanel candlesSection = createCandlesSection();
        centerPanel.add(candlesSection);

        add(centerPanel, BorderLayout.CENTER);

        String downloadsPath = System.getProperty("user.home");
        JLabel infoLabel = new JLabel("💡 T-Bank Invest API → PostgreSQL → CSV → " + downloadsPath, SwingConstants.CENTER);
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        add(infoLabel, BorderLayout.SOUTH);
    }

    private JPanel createBondsSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("📥 1. Экспорт облигаций в БД"));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JLabel label = new JLabel("Загрузить список облигаций из T-Bank API в таблицу public.exportdata");
        label.setFont(new Font("Arial", Font.PLAIN, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        bondsButton = new JButton("📥 Экспортировать облигации в БД");
        bondsButton.setFont(new Font("Arial", Font.BOLD, 14));
        bondsButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        bondsButton.addActionListener(e -> exportBondsToDatabase());

        section.add(label);
        section.add(Box.createVerticalStrut(10));
        section.add(bondsButton);

        return section;
    }

    private JPanel createAnalysisSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("🔍 2. Анализ облигаций"));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JLabel label = new JLabel("Анализ облигаций с расчетом цен покупки/продажи, доходности, dlong, волатильности. " +
                "Рекомендации на основе 4 критериев: волатильность, тренд, средний объем, погашение.");
        label.setFont(new Font("Arial", Font.PLAIN, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton analysisButton = new JButton("🔍 Запустить анализ облигаций");
        analysisButton.setFont(new Font("Arial", Font.BOLD, 14));
        analysisButton.setBackground(new Color(52, 152, 219));
        analysisButton.setForeground(Color.WHITE);
        analysisButton.setFocusPainted(false);
        analysisButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        analysisButton.addActionListener(e -> showBondsAnalysisDialog());

        section.add(label);
        section.add(Box.createVerticalStrut(10));
        section.add(analysisButton);

        return section;
    }

    private JPanel createBacktestSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("📊 3. Бэктест стратегии"));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JLabel label = new JLabel("Исторический тест торговой стратегии по облигациям. " +
                "Проверка прибыльности и расчет комиссий (0.04%) за выбранный период.");
        label.setFont(new Font("Arial", Font.PLAIN, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton backtestButton = new JButton("📊 Запустить бэктест");
        backtestButton.setFont(new Font("Arial", Font.BOLD, 14));
        backtestButton.setBackground(new Color(155, 89, 182));
        backtestButton.setForeground(Color.WHITE);
        backtestButton.setFocusPainted(false);
        backtestButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        backtestButton.addActionListener(e -> showBacktestDialog());

        section.add(label);
        section.add(Box.createVerticalStrut(10));
        section.add(backtestButton);

        return section;
    }

    private JPanel createCandlesSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("📈 4. Экспорт свечей в CSV"));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JLabel label = new JLabel("Экспорт исторических свечей OHLCV по FIGI в CSV");
        label.setFont(new Font("Arial", Font.PLAIN, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton candlesButton = new JButton("📈 Экспорт свечей в CSV");
        candlesButton.setFont(new Font("Arial", Font.BOLD, 14));
        candlesButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        candlesButton.addActionListener(e -> showCandlesExportDialog());

        section.add(label);
        section.add(Box.createVerticalStrut(10));
        section.add(candlesButton);

        return section;
    }

    private void exportBondsToDatabase() {
        log.info("🚀 Запуск экспорта облигаций в БД");
        bondsButton.setEnabled(false);
        bondsButton.setText("⏳ Загрузка...");

        AsyncTask.execute(
                () -> {
                    // ✅ ИСПРАВЛЕНО: Используем APIURL и APIPORT (константы, не методы)
                    BondsService bondsService = new BondsService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT
                    );
                    log.info("📡 Запрос списка облигаций из API...");
                    BondsResponse response = bondsService.getBonds();
                    java.util.List bonds = response.getInstrumentsList();
                    log.info("✅ Получено облигаций из API: {}", bonds.size());

                    BondsRepository repository = new BondsRepository();
                    int exportedCount = repository.exportBonds(bonds);
                    int totalRows = repository.getRowCount();

                    log.info("💾 Экспорт завершен. Экспортировано: {}, Всего строк: {}", exportedCount, totalRows);
                    return new Object[]{exportedCount, totalRows};
                },
                result -> {
                    int exportedCount = (int) ((Object[]) result)[0];
                    int totalRows = (int) ((Object[]) result)[1];
                    DialogUtils.showSuccess(parentFrame,
                            String.format("✅ Экспорт завершен!\n\nЭкспортировано в public.exportdata:\n• %d облигаций\n• Всего строк: %d",
                                    exportedCount, totalRows));
                    bondsButton.setEnabled(true);
                    bondsButton.setText("📥 Экспортировать облигации в БД");
                },
                error -> {
                    log.error("❌ Ошибка экспорта облигаций", error);
                    DialogUtils.showError(parentFrame, "❌ Ошибка: " + error.getMessage());
                    bondsButton.setEnabled(true);
                    bondsButton.setText("📥 Экспортировать облигации в БД");
                }
        );
    }

    /**
     * ✅ ИСПРАВЛЕННЫЙ МЕТОД - ПОЛУЧАЕМ РЕЗУЛЬТАТ ОТ ДИАЛОГА
     */
    private void showBondsAnalysisDialog() {
        log.info("🔍 Открытие диалога анализа облигаций");

        BondsAnalysisDialog dialog = new BondsAnalysisDialog(parentFrame);
        BondsAnalysisService.BondsFilterCriteria criteria = dialog.showDialog();

        if (criteria != null) {
            log.info("✅ Пользователь подтвердил параметры анализа");
            runBondsAnalysis(criteria);
        } else {
            log.info("❌ Пользователь отменил анализ");
        }
    }

    /**
     * ✅ ИСПРАВЛЕННЫЙ МЕТОД ЗАПУСКА АНАЛИЗА
     */
    private void runBondsAnalysis(BondsAnalysisService.BondsFilterCriteria criteria) {
        log.info("🚀 Запуск анализа облигаций с критериями: {}", criteria);

        AsyncTask.execute(
                () -> {
                    BondsService bondsService = new BondsService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT
                    );
                    log.info("📡 Получение списка облигаций из API...");
                    BondsResponse response = bondsService.getBonds();
                    List bonds = response.getInstrumentsList();
                    log.info("✅ Получено облигаций из API: {}", bonds.size());

                    // ✅ ИСПРАВЛЕНИЕ: ФИЛЬТРАЦИЯ ПЕРЕД АНАЛИЗОМ
                    BondsAnalysisService analysisService = new BondsAnalysisService();

                    log.info("🔍 Фильтрация облигаций по критериям...");
                    List filteredBonds = analysisService.filterBonds(bonds, criteria);
                    log.info("✅ После фильтрации осталось облигаций: {}", filteredBonds.size());

                    CandlesApiService candlesService = new CandlesApiService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT
                    );

                    log.info("📊 Анализ {} отфильтрованных облигаций...", filteredBonds.size());
                    List<BondsAnalysisService.BondAnalysisResult> results =
                            analysisService.analyzeBonds(filteredBonds, candlesService, criteria);
                    log.info("✅ Анализ завершен. Найдено облигаций: {}", results.size());
                    return results;
                },
                results -> {
                    @SuppressWarnings("unchecked")
                    List<BondsAnalysisService.BondAnalysisResult> bondResults =
                            (List<BondsAnalysisService.BondAnalysisResult>) results;

                    if (bondResults.isEmpty()) {
                        DialogUtils.showWarning(parentFrame,
                                "⚠️ Облигаций не найдено по заданным критериям");
                    } else {
                        log.info("📊 Открытие окна результатов анализа");
                        AnalysisResultsDialog resultsDialog = new AnalysisResultsDialog(parentFrame, bondResults);
                        resultsDialog.setVisible(true);
                    }
                },
                error -> {
                    log.error("❌ Ошибка анализа облигаций", error);
                    DialogUtils.showError(parentFrame, "❌ Ошибка: " + error.getMessage());
                }
        );
    }


    private void showBacktestDialog() {
        log.info("📊 Открытие диалога бэктеста");

        BacktestDialog dialog = new BacktestDialog(parentFrame);
        Object[] result = (Object[]) dialog.showDialog();

        if (result != null) {
            log.info("✅ Пользователь подтвердил параметры бэктеста");
            runBacktest(result);
        } else {
            log.info("❌ Пользователь отменил бэктест");
        }
    }

    private void runBacktest(Object[] params) {
        java.time.LocalDate startDate = (java.time.LocalDate) params[0];
        java.time.LocalDate endDate = (java.time.LocalDate) params[1];
        BondStrategyBacktestService.BacktestFilters filters = (BondStrategyBacktestService.BacktestFilters) params[2];

        log.info("🚀 Запуск бэктеста: {} - {}", startDate, endDate);

        AsyncTask.execute(
                () -> {
                    CandlesApiService candlesApi = new CandlesApiService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT
                    );
                    BondsRepository bondsRepo = new BondsRepository();
                    ParametersRepository paramsRepo = new ParametersRepository();

                    BondStrategyBacktestService backtestService = new BondStrategyBacktestService(
                            candlesApi,
                            bondsRepo,
                            paramsRepo
                    );
                   log.info("📊 Выполнение бэктеста стратегии...");
                    BondStrategyBacktestService.BacktestReport report = backtestService.runBacktest(startDate, endDate, filters);
                    log.info("✅ Бэктест завершен. Облигаций: {}, Сделок: {}", report.getTotalBonds(), report.getTotalTrades());
                    return report;
                },
                report -> {
                    BondStrategyBacktestService.BacktestReport backtestReport =
                            (BondStrategyBacktestService.BacktestReport) report;
                    log.info("📊 Открытие окна результатов бэктеста");
                    BacktestReportDialog reportDialog = new BacktestReportDialog(parentFrame, backtestReport);
                    reportDialog.setVisible(true);
                },
                error -> {
                    log.error("❌ Ошибка бэктеста", error);
                    DialogUtils.showError(parentFrame, "❌ Ошибка: " + error.getMessage());
                }
        );
    }

    private void showCandlesExportDialog() {
        log.info("📈 Открытие диалога экспорта свечей");
        CandlesExportDialog dialog = new CandlesExportDialog(parentFrame);
        dialog.setVisible(true);
    }
}
