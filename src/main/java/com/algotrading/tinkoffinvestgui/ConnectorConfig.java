package com.algotrading.tinkoffinvestgui;

import ru.ttech.piapi.core.connector.ConnectorConfiguration;

public class ConnectorConfig {

    private final ConnectorConfiguration config;
    private final String token;

    public ConnectorConfig(String propertiesPath) {
        this.config = ConnectorConfiguration.loadPropertiesFromFile(propertiesPath);
        this.token = extractTokenFromConfig(this.config);
    }

    public ConnectorConfiguration getConfig() {
        return config;
    }

    public String getToken() {
        return token;
    }

    /**
     * Получает API URL из конфигурации
     */
    public String getApiUrl() {
        try {
            java.lang.reflect.Method getApiUrlMethod = config.getClass().getMethod("getApiUrl");
            return (String) getApiUrlMethod.invoke(config);
        } catch (Exception e) {
            return "invest-public-api.tinkoff.ru"; // значение по умолчанию
        }
    }

    /**
     * Получает API порт из конфигурации
     */
    public int getApiPort() {
        try {
            java.lang.reflect.Method getApiPortMethod = config.getClass().getMethod("getApiPort");
            Object result = getApiPortMethod.invoke(config);
            if (result instanceof Integer) {
                return (Integer) result;
            }
            return 443; // значение по умолчанию
        } catch (Exception e) {
            return 443; // значение по умолчанию
        }
    }

    private static String extractTokenFromConfig(ConnectorConfiguration config) {
        try {
            java.lang.reflect.Method getTokenMethod = config.getClass().getMethod("getToken");
            return (String) getTokenMethod.invoke(config);
        } catch (Exception e) {
            return null;
        }
    }
}
