package com.example.weathersdk.model;

/**
 * Enumeration of measurement units supported by the OpenWeather API.
 *
 * <ul>
 *     <li>{@link #STANDARD} – temperatures in Kelvin and wind speed in m/s (default);</li>
 *     <li>{@link #METRIC} – temperatures in degrees Celsius and wind speed in m/s;</li>
 *     <li>{@link #IMPERIAL} – temperatures in degrees Fahrenheit and wind speed in mph.</li>
 * </ul>
 */
public enum Units {
    STANDARD("standard"),
    METRIC("metric"),
    IMPERIAL("imperial");

    private final String apiValue;

    Units(String apiValue) {
        this.apiValue = apiValue;
    }

    /** Returns the string representation used in API requests. */
    public String getApiValue() {
        return apiValue;
    }
}