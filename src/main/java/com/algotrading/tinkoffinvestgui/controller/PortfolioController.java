package com.algotrading.tinkoffinvestgui.controller;

import com.algotrading.tinkoffinvestgui.model.Portfolio;
import com.algotrading.tinkoffinvestgui.service.PortfolioService;
import com.algotrading.tinkoffinvestgui.util.SwingUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Контроллер для управления портфелем
 */
public class PortfolioController {
    
    private final PortfolioService portfolioService;
    private final List<Consumer<Portfolio>> observers = new ArrayList<>();
    private Portfolio currentPortfolio;
    
    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }
    
    public void addObserver(Consumer<Portfolio> observer) {
        observers.add(observer);
    }
    
    public void refreshPortfolio() {
        refreshPortfolio(() -> {}, e -> {});
    }
    
    public void refreshPortfolio(Runnable onComplete, Consumer<Exception> onError) {
        SwingUtils.runInBackground(
            () -> portfolioService.fetchPortfolio(),
            portfolio -> {
                this.currentPortfolio = portfolio;
                notifyObservers(portfolio);
                onComplete.run();
            },
            error -> {
                onError.accept(error);
                SwingUtils.showError(null, "Ошибка загрузки портфеля", error);
            }
        );
    }
    
    public Portfolio getCurrentPortfolio() {
        return currentPortfolio;
    }
    
    private void notifyObservers(Portfolio portfolio) {
        javax.swing.SwingUtilities.invokeLater(() -> 
            observers.forEach(observer -> observer.accept(portfolio))
        );
    }
}
