package com.algotrading.tinkoffinvestgui;

import com.algotrading.tinkoffinvestgui.config.AppConstants;
import com.algotrading.tinkoffinvestgui.service.OrdersScheduler;
import com.algotrading.tinkoffinvestgui.repository.ParametersRepository;
import com.algotrading.tinkoffinvestgui.repository.InstrumentsRepository;
import com.algotrading.tinkoffinvestgui.repository.OrdersRepository;
import com.algotrading.tinkoffinvestgui.ui.panels.ExportAnalysisPanel;
import com.algotrading.tinkoffinvestgui.ui.panels.InstrumentsPanel;
import com.algotrading.tinkoffinvestgui.ui.panels.PortfolioPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Главный класс GUI приложения для работы с Tinkoff Invest API
 * 
 * РЕФАКТОРИНГ: Разделен на панели и диалоги для улучшения читаемости и поддержки
 */
public class TinkoffInvestGui extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(TinkoffInvestGui.class);

    // Панели
    private InstrumentsPanel instrumentsPanel;
    private PortfolioPanel portfolioPanel;
    private ExportAnalysisPanel exportPanel;

    // Компоненты
    private JTabbedPane tabbedPane;
    private OrdersScheduler ordersScheduler;

    public TinkoffInvestGui() {
        log.info("🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀 Инициализация Tinkoff Invest GUI 🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀🚀");
        initializeUI();
        startServices();
    }

    /**
     * Инициализация пользовательского интерфейса
     */
    private void initializeUI() {
        // Настройки окна
        setTitle("Tinkoff Invest - Управление инструментами и торговля");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        setLayout(new BorderLayout());
        setSize(AppConstants.WINDOW_WIDTH, AppConstants.WINDOW_HEIGHT);
        setLocationRelativeTo(null);

        // Создание панелей
        instrumentsPanel = new InstrumentsPanel(this);
        portfolioPanel = new PortfolioPanel(this);
        exportPanel = new ExportAnalysisPanel(this);

        // Добавление вкладок
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Инструменты", instrumentsPanel);
        tabbedPane.addTab("Портфель", portfolioPanel);
        tabbedPane.addTab("Экспорт и Анализ", exportPanel);

        add(tabbedPane, BorderLayout.CENTER);
    }

    /**
     * Запуск сервисов
     */
    private void startServices() {
        portfolioPanel.startAutoUpdate();
        instrumentsPanel.loadInstruments();
        portfolioPanel.updateAccountsAndPortfolio();
        initOrdersScheduler();
        log.info("✅ GUI инициализирован");
    }

    /**
     * Инициализация планировщика заявок с параметрами из БД (starttime)
     */
    private void initOrdersScheduler() {
        log.info("🔧 Инициализация планировщика заявок (starttime)");

        ParametersRepository paramsRepo = new ParametersRepository();
        InstrumentsRepository instrumentsRepo = new InstrumentsRepository();
        OrdersRepository ordersRepo = new OrdersRepository();

        // Задача выставления заявок (запускается ПОСЛЕ подготовки данных)
        Runnable ordersTask = () -> {
            try {
                log.info("📤 GUI: Запуск задачи выставления заявок");
                SwingUtilities.invokeLater(() -> {
                    try {
                        instrumentsPanel.sendOrdersToExchange();
                    } catch (Exception e) {
                        log.error("❌ Ошибка выставления заявок", e);
                    }
                });
            } catch (Exception e) {
                log.error("❌ Ошибка при выполнении ordersTask", e);
            }
        };

        Runnable refreshTableCallback = instrumentsPanel::refreshTable;

        // Создаём планировщик с новой логикой (DB скрипт → расчёт цен → заявки)
        ordersScheduler = new OrdersScheduler(paramsRepo, instrumentsRepo, ordersRepo, ordersTask,refreshTableCallback );
        ordersScheduler.start();

        log.info("✅ Планировщик запущен (проверка каждую 1 минуту)");
    }


    /**
     * Остановка приложения
     */
    private void shutdown() {
        log.info("🛑 Остановка приложения");

        if (ordersScheduler != null) {
            ordersScheduler.stop();
        }

        portfolioPanel.stopAutoUpdate();
        System.exit(0);
    }

    /**
     * Точка входа в приложение
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new TinkoffInvestGui().setVisible(true);
        });
    }
}
