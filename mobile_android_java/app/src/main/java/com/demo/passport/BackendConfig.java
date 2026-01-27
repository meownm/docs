package com.demo.passport;

public final class BackendConfig {
    public static final String DEFAULT_BASE_URL = "http://192.168.1.125:30450";
    private static volatile String baseUrl = DEFAULT_BASE_URL;

    public static String getBaseUrl() {
        return baseUrl;
    }

    public static void setBaseUrlForTesting(String newBaseUrl) {
        baseUrl = newBaseUrl;
    }

    private BackendConfig() {}
}
