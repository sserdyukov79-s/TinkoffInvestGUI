package com.algotrading.tinkoffinvestgui.exception;

/**
 * Базовое исключение для приложения
 */
public class TinkoffInvestException extends RuntimeException {
    
    public TinkoffInvestException(String message) {
        super(message);
    }
    
    public TinkoffInvestException(String message, Throwable cause) {
        super(message, cause);
    }
}
