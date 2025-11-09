package com.example.weathersdk;

/**
 * Enumeration representing the supported operational modes of the SDK.
 * <p>
 * The SDK can operate in two modes:
 * <ul>
 *     <li>ON_DEMAND – the SDK contacts the remote OpenWeather API only when a
 *     client requests weather information for a city.  Cached values are
 *     returned whenever they are still considered fresh.</li>
 *     <li>POLLING – the SDK maintains a background task that periodically
 *     refreshes weather information for all cached cities.  This mode
 *     provides zero‑latency responses at the expense of additional API
 *     requests.</li>
 * </ul>
 */
public enum WeatherMode {
    /**
     * Update weather data lazily when a client requests it.  Cached values
     * remain until they expire and are fetched again.
     */
    ON_DEMAND,

    /**
     * Maintain up‑to‑date weather information by polling the API at a fixed
     * interval for all cached cities.  When a client requests data, the
     * response is returned immediately from the cache.
     */
    POLLING
}