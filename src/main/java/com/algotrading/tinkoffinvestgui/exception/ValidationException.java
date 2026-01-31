package com.algotrading.tinkoffinvestgui.exception;

/**
 * Исключение при валидации данных
 */
public class ValidationException extends TinkoffInvestException {
    
    private final String fieldName;
    
    public ValidationException(String message, String fieldName) {
        super(message);
        this.fieldName = fieldName;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    @Override
    public String getMessage() {
        return String.format("Поле '%s': %s", fieldName, super.getMessage());
    }
}
