package com.algotrading.tinkoffinvestgui.service;

import com.algotrading.tinkoffinvestgui.model.Portfolio;
import com.algotrading.tinkoffinvestgui.model.Position;
import com.algotrading.tinkoffinvestgui.util.MoneyConverter;
import ru.tinkoff.piapi.contract.v1.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для работы с портфелем
 */
public class PortfolioService {
    
    private final TinkoffApiService apiService;
    
    public PortfolioService(TinkoffApiService apiService) {
        this.apiService = apiService;
    }
    
    public Portfolio fetchPortfolio() {
        PortfolioResponse response = apiService.getPortfolio();
        
        List<Position> positions = new ArrayList<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalProfitLoss = BigDecimal.ZERO;
        
        for (PortfolioPosition apiPosition : response.getPositionsList()) {
            String figi = apiPosition.getFigi();
            
            try {
                var instrument = apiService.getInstrumentByFigi(figi);
                var lastPrice = apiService.getLastPrice(figi);
                
                BigDecimal quantity = MoneyConverter.toBigDecimal(apiPosition.getQuantity());
                BigDecimal averagePrice = apiPosition.hasAveragePositionPrice() ?
                        MoneyConverter.toBigDecimal(apiPosition.getAveragePositionPrice()) : BigDecimal.ZERO;
                BigDecimal currentPrice = MoneyConverter.toBigDecimal(lastPrice.getPrice());
                
                Position position = new Position.Builder()
                        .figi(figi)
                        .ticker(instrument.getTicker())
                        .name(instrument.getName())
                        .instrumentType(instrument.getInstrumentType())
                        .quantity(quantity)
                        .averagePrice(averagePrice)
                        .currentPrice(currentPrice)
                        .currency(instrument.getCurrency())
                        .build();
                
                positions.add(position);
                totalValue = totalValue.add(position.getTotalValue());
                totalProfitLoss = totalProfitLoss.add(position.getProfitLoss());
                
            } catch (Exception e) {
                System.err.println("Ошибка обработки позиции " + figi + ": " + e.getMessage());
            }
        }
        
        return new Portfolio.Builder()
                .positions(positions)
                .totalValue(totalValue)
                .totalProfitLoss(totalProfitLoss)
                .currency("RUB")
                .build();
    }
}
