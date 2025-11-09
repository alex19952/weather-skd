package com.example.weathersdk.api;

import com.example.weathersdk.model.WeatherRequest;
import com.example.weathersdk.model.WeatherResponse;
import com.example.weathersdk.WeatherSDKException;

import java.util.concurrent.CompletableFuture;

/**
 * High‑level interface describing operations supported by the weather SDK.  A
 * service is responsible for orchestrating caching, polling and network
 * communications to fulfil weather queries.
 */
public interface WeatherService {
    /**
     * Retrieves the current weather for the supplied request synchronously.
     * Implementations may consult caches or initiate network requests.
     *
     * @param request the query parameters
     * @return simplified weather data
     * @throws WeatherSDKException if retrieval fails
     */
    WeatherResponse getWeather(WeatherRequest request) throws WeatherSDKException;

    /**
     * Retrieves the current weather asynchronously.  The returned future
     * completes with the response or completes exceptionally with
     * {@link WeatherSDKException}.
     *
     * @param request the query parameters
     * @return a future with weather data
     */
    CompletableFuture<WeatherResponse> getWeatherAsync(WeatherRequest request);

    /**
     * Shuts down all background resources used by the service (e.g. scheduled
     * pollers).  After shutdown the service should not be used.
     */
    void shutdown();
}