package com.algotrading.tinkoffinvestgui.ui.panels;

import ru.tinkoff.piapi.contract.v1.BondsResponse;
import com.algotrading.tinkoffinvestgui.api.BondsService;
import com.algotrading.tinkoffinvestgui.repository.BondsRepository;
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

/**
 * Панель экспорта и анализа
 */
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

        // Заголовок
        JLabel title = new JLabel("Экспорт данных из Tinkoff Invest", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        add(title, BorderLayout.NORTH);

        // Центральная панель с секциями
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        // 1. Секция: Экспорт облигаций
        JPanel bondsSection = createBondsSection();
        centerPanel.add(bondsSection);
        centerPanel.add(Box.createVerticalStrut(20));

        // 2. Секция: Анализ облигаций
        JPanel analysisSection = createAnalysisSection();
        centerPanel.add(analysisSection);
        centerPanel.add(Box.createVerticalStrut(20));

        // 3. Секция: Бэктестинг
        JPanel backtestSection = createBacktestSection();
        centerPanel.add(backtestSection);
        centerPanel.add(Box.createVerticalStrut(20));

        // 4. Секция: Экспорт свечей
        JPanel candlesSection = createCandlesSection();
        centerPanel.add(candlesSection);

        add(centerPanel, BorderLayout.CENTER);

        // Информация внизу
        String downloadsPath = System.getProperty("user.home");
        JLabel infoLabel = new JLabel(
                "T-Bank Invest API → База данных PostgreSQL → CSV файлы в " + downloadsPath,
                SwingConstants.CENTER);
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        add(infoLabel, BorderLayout.SOUTH);
    }

    /**
     * Секция экспорта облигаций
     */
    private JPanel createBondsSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("Облигации"));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JLabel label = new JLabel("Загрузить облигации из T-Bank API в базу данных public.exportdata");
        label.setFont(new Font("Arial", Font.PLAIN, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        bondsButton = new JButton("Экспортировать облигации");
        bondsButton.setFont(new Font("Arial", Font.BOLD, 14));
        bondsButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        bondsButton.addActionListener(e -> exportBondsToDatabase());

        section.add(label);
        section.add(Box.createVerticalStrut(10));
        section.add(bondsButton);

        return section;
    }

    /**
     * Секция анализа облигаций
     */
    private JPanel createAnalysisSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("Анализ облигаций"));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JLabel label = new JLabel(
                "Анализ волатильности и расчет цен покупки/продажи по всем облигациям с фильтрацией (валюта, dlong, " +
                        "срок погашения). Использует период анализа 4 мес из БД, волатильность для цены покупки, " +
                        "стандартная наценка для цены продажи."
        );
        label.setFont(new Font("Arial", Font.PLAIN, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton analysisButton = new JButton("Анализировать облигации");
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

    /**
     * Секция бэктестинга
     */
    private JPanel createBacktestSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("Бэктестинг стратегии"));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JLabel label = new JLabel(
                "Историческое тестирование стратегии \"ловец дна\" на исторических данных с учётом комиссий. " +
                        "Показывает прибыльность стратегии за выбранный период."
        );
        label.setFont(new Font("Arial", Font.PLAIN, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton backtestButton = new JButton("Запустить бэктестинг");
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

    /**
     * Секция экспорта свечей
     */
    private JPanel createCandlesSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("Экспорт свечей в CSV"));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JLabel label = new JLabel("Экспорт исторических OHLCV свечей в CSV файл");
        label.setFont(new Font("Arial", Font.PLAIN, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton candlesButton = new JButton("Экспортировать свечи в CSV");
        candlesButton.setFont(new Font("Arial", Font.BOLD, 14));
        candlesButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        candlesButton.addActionListener(e -> showCandlesExportDialog());

        section.add(label);
        section.add(Box.createVerticalStrut(10));
        section.add(candlesButton);

        return section;
    }

    /**
     * Экспорт облигаций в БД
     */
    private void exportBondsToDatabase() {
        log.info("📤 Экспорт облигаций в базу данных");
        bondsButton.setEnabled(false);
        bondsButton.setText("Экспорт...");

        AsyncTask.execute(
                () -> {
                    BondsService bondsService = new BondsService(
                            ConnectorConfig.getApiToken(),
                            ConnectorConfig.API_URL,
                            ConnectorConfig.API_PORT
                    );

                    log.info("📡 Загрузка облигаций из API...");
                    BondsResponse response = bondsService.getBonds();
                    java.util.List bonds = response.getInstrumentsList();
                    log.info("📊 Получено облигаций из API: {}", bonds.size());

                    BondsRepository repository = new BondsRepository();
                    int exportedCount = repository.exportBonds(bonds);
                    int totalRows = repository.getRowCount();

                    log.info("✅ Экспорт завершён. Обновлено строк: {}, всего строк: {}",
                            exportedCount, totalRows);

                    return new Object[]{exportedCount, totalRows};
                },
                result -> {
                    int exportedCount = (int) ((Object[]) result)[0];
                    int totalRows = (int) ((Object[]) result)[1];

                    DialogUtils.showSuccess(parentFrame,
                            "Облигации успешно экспортированы!\n\n" +
                                    "Таблица: public.exportdata\n" +
                                    "Обновлено строк: " + exportedCount + "\n" +
                                    "Всего строк: " + totalRows);

                    bondsButton.setEnabled(true);
                    bondsButton.setText("Экспортировать облигации");
                },
                error -> {
                    log.error("❌ Ошибка экспорта облигаций", error);
                    DialogUtils.showError(parentFrame, error.getMessage());
                    bondsButton.setEnabled(true);
                    bondsButton.setText("Экспортировать облигации");
                }
        );
    }

    /**
     * Диалог параметров анализа облигаций
     */
    private void showBondsAnalysisDialog() {
        log.info("🔍 Открытие диалога параметров анализа облигаций");
        BondsAnalysisDialog dialog = new BondsAnalysisDialog(parentFrame);
        dialog.setVisible(true);
    }

    /**
     * Диалог параметров бэктестинга
     */
    private void showBacktestDialog() {
        log.info("🧪 Открытие диалога параметров бэктестинга");
        BacktestDialog dialog = new BacktestDialog(parentFrame);
        dialog.setVisible(true);
    }

    /**
     * Диалог экспорта свечей
     */
    private void showCandlesExportDialog() {
        log.info("📥 Открытие диалога экспорта свечей");
        CandlesExportDialog dialog = new CandlesExportDialog(parentFrame);
        dialog.setVisible(true);
    }
}
