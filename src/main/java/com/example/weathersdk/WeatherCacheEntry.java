package com.example.weathersdk;

import com.example.weathersdk.model.WeatherResponse;

import java.time.Instant;

/**
 * Internal container class used to track cached weather responses along with
 * their retrieval time.  The SDK uses these entries to determine when
 * cached values have expired (after ten minutes) or when they should be
 * refreshed in polling mode.
 */
class WeatherCacheEntry {
    /**
     * The weather response returned by the OpenWeather API and simplified
     * by {@link WeatherResponse}.
     */
    final WeatherResponse response;

    /**
     * The time when this response was last updated.
     */
    final Instant timestamp;

    WeatherCacheEntry(WeatherResponse response, Instant timestamp) {
        this.response = response;
        this.timestamp = timestamp;
    }
}