package com.algotrading.tinkoffinvestgui;

import ru.ttech.piapi.core.connector.ConnectorConfiguration;

public class ConnectorConfig {
    private final ConnectorConfiguration config;
    private final String token;

    public ConnectorConfig(String propertiesPath) {
        this.config = ConnectorConfiguration.loadPropertiesFromFile(propertiesPath);
        this.token = extractTokenFromConfig(this.config);
    }

    public ConnectorConfiguration getConfig() { return config; }
    public String getToken() { return token; }

    private static String extractTokenFromConfig(ConnectorConfiguration config) {
        try {
            java.lang.reflect.Method getTokenMethod = config.getClass().getMethod("getToken");
            return (String) getTokenMethod.invoke(config);
        } catch (Exception e) {
            return null;
        }
    }
}
