package com.algotrading.tinkoffinvestgui.exception;

/**
 * Исключение при работе с Tinkoff Invest API
 */
public class ApiException extends TinkoffInvestException {
    
    private final String apiMethod;
    
    public ApiException(String message, String apiMethod) {
        super(message);
        this.apiMethod = apiMethod;
    }
    
    public ApiException(String message, String apiMethod, Throwable cause) {
        super(message, cause);
        this.apiMethod = apiMethod;
    }
    
    public String getApiMethod() {
        return apiMethod;
    }
    
    @Override
    public String getMessage() {
        return String.format("[%s] %s", apiMethod, super.getMessage());
    }
}
