package com.example.weathersdk.client;

import com.example.weathersdk.model.Units;
import com.example.weathersdk.model.WeatherRequest;
import com.example.weathersdk.model.WeatherResponse;
import com.example.weathersdk.WeatherSDKException;

import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over the network layer used to call the OpenWeather API.
 * Implementations may use different HTTP clients (e.g. `java.net.http.HttpClient`,
 * OkHttp, Spring WebClient).  This interface allows for testability and
 * flexibility when integrating with other systems.
 */
public interface WeatherClient {
    /**
     * Performs a synchronous call to the OpenWeather API for the given request.
     *
     * @param request the weather request containing city or coordinates, units and language
     * @return the simplified weather response
     * @throws WeatherSDKException if communication fails or API returns an error
     */
    WeatherResponse fetchWeather(WeatherRequest request) throws WeatherSDKException;

    /**
     * Performs an asynchronous call to the OpenWeather API for the given request.
     * The returned future completes exceptionally with {@link WeatherSDKException} if an error occurs.
     *
     * @param request the weather request containing city or coordinates, units and language
     * @return a future yielding the simplified weather response
     */
    CompletableFuture<WeatherResponse> fetchWeatherAsync(WeatherRequest request);
}