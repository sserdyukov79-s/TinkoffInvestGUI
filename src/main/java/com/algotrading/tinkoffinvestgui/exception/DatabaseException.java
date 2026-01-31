package com.algotrading.tinkoffinvestgui.exception;

/**
 * Исключение при работе с базой данных
 */
public class DatabaseException extends TinkoffInvestException {
    
    public DatabaseException(String message) {
        super(message);
    }
    
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
