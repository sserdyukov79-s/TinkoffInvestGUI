package com.algotrading.tinkoffinvestgui.validation;

import com.algotrading.tinkoffinvestgui.config.AppConstants;
import com.algotrading.tinkoffinvestgui.entity.Instrument;
import com.algotrading.tinkoffinvestgui.exception.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Валидатор для Instrument
 */
public class InstrumentValidator {
    
    /**
     * Валидирует инструмент перед сохранением
     */
    public static void validate(Instrument instrument) {
        List<String> errors = new ArrayList<>();
        
        // Обязательные поля
        if (instrument.getName() == null || instrument.getName().trim().isEmpty()) {
            errors.add("Название инструмента обязательно");
        }
        
        if (instrument.getIsin() == null || instrument.getIsin().trim().isEmpty()) {
            errors.add("ISIN обязателен");
        } else if (!isValidIsin(instrument.getIsin())) {
            errors.add("Неверный формат ISIN (должен быть 12 символов)");
        }
        
        if (instrument.getBookdate() == null) {
            errors.add("Дата обязательна");
        } else if (instrument.getBookdate().isAfter(LocalDate.now())) {
            errors.add("Дата не может быть в будущем");
        }
        
        // Приоритет
        if (instrument.getPriority() == null) {
            errors.add("Приоритет обязателен");
        } else if (instrument.getPriority() < AppConstants.MIN_PRIORITY || 
                   instrument.getPriority() > AppConstants.MAX_PRIORITY) {
            errors.add(String.format("Приоритет должен быть от %d до %d", 
                AppConstants.MIN_PRIORITY, AppConstants.MAX_PRIORITY));
        }
        
        // FIGI (опционально, но если указан - должен быть корректным)
        if (instrument.getFigi() != null && !instrument.getFigi().trim().isEmpty()) {
            if (!isValidFigi(instrument.getFigi())) {
                errors.add("Неверный формат FIGI (должен начинаться с BBG)");
            }
        }
        
        // Валидация цен и количеств
        validateBuyOrder(instrument, errors);
        validateSellOrder(instrument, errors);
        
        // Если есть ошибки - выбрасываем исключение
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join("; ", errors), "Instrument");
        }
    }
    
    /**
     * Валидирует заявку на покупку
     */
    private static void validateBuyOrder(Instrument instrument, List<String> errors) {
        if (instrument.getBuyPrice() != null || instrument.getBuyQuantity() != null) {
            if (instrument.getBuyPrice() == null) {
                errors.add("Цена покупки не указана, но указано количество");
            } else if (instrument.getBuyPrice().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Цена покупки должна быть больше 0");
            }
            
            if (instrument.getBuyQuantity() == null) {
                errors.add("Количество покупки не указано, но указана цена");
            } else if (instrument.getBuyQuantity() < AppConstants.MIN_QUANTITY || 
                       instrument.getBuyQuantity() > AppConstants.MAX_QUANTITY) {
                errors.add(String.format("Количество покупки должно быть от %d до %d", 
                    AppConstants.MIN_QUANTITY, AppConstants.MAX_QUANTITY));
            }
        }
    }
    
    /**
     * Валидирует заявку на продажу
     */
    private static void validateSellOrder(Instrument instrument, List<String> errors) {
        if (instrument.getSellPrice() != null || instrument.getSellQuantity() != null) {
            if (instrument.getSellPrice() == null) {
                errors.add("Цена продажи не указана, но указано количество");
            } else if (instrument.getSellPrice().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Цена продажи должна быть больше 0");
            }
            
            if (instrument.getSellQuantity() == null) {
                errors.add("Количество продажи не указано, но указана цена");
            } else if (instrument.getSellQuantity() < AppConstants.MIN_QUANTITY || 
                       instrument.getSellQuantity() > AppConstants.MAX_QUANTITY) {
                errors.add(String.format("Количество продажи должно быть от %d до %d", 
                    AppConstants.MIN_QUANTITY, AppConstants.MAX_QUANTITY));
            }
        }
        
        // Проверка: если цена продажи меньше или равна цене покупки
        if (instrument.getBuyPrice() != null && instrument.getSellPrice() != null) {
            if (instrument.getSellPrice().compareTo(instrument.getBuyPrice()) <= 0) {
                errors.add("Цена продажи должна быть выше цены покупки");
            }
        }
    }
    
    /**
     * Проверяет валидность ISIN (должен быть 12 символов)
     */
    private static boolean isValidIsin(String isin) {
        if (isin == null) return false;
        String cleaned = isin.trim().toUpperCase();
        return cleaned.matches("^[A-Z]{2}[A-Z0-9]{9}[0-9]$");
    }
    
    /**
     * Проверяет валидность FIGI (должен начинаться с BBG)
     */
    private static boolean isValidFigi(String figi) {
        if (figi == null) return false;
        String cleaned = figi.trim().toUpperCase();
        return cleaned.startsWith("BBG") && cleaned.length() == 12;
    }
    
    /**
     * Быстрая проверка - есть ли хотя бы одна заявка (покупка или продажа)
     */
    public static boolean hasAnyOrder(Instrument instrument) {
        boolean hasBuy = instrument.getBuyPrice() != null && 
                        instrument.getBuyQuantity() != null && 
                        instrument.getBuyQuantity() > 0;
        
        boolean hasSell = instrument.getSellPrice() != null && 
                         instrument.getSellQuantity() != null && 
                         instrument.getSellQuantity() > 0;
        
        return hasBuy || hasSell;
    }
}
