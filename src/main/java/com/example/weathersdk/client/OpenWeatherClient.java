package com.example.weathersdk.client;

import com.example.weathersdk.WeatherSDKException;
import com.example.weathersdk.model.WeatherRequest;
import com.example.weathersdk.model.WeatherResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Default implementation of {@link WeatherClient} that communicates with
 * OpenWeather via HTTP using {@link HttpURLConnection}.  Supports synchronous
 * and asynchronous operations and parses JSON responses into simplified
 * {@link WeatherResponse} instances.
 */
public class OpenWeatherClient implements WeatherClient {
    /** Base URL for the OpenWeather current weather endpoint. */
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/weather";
    private final String apiKey;
    private final ExecutorService asyncExecutor;

    /** Logger for HTTP calls. */
    private static final Logger log = LoggerFactory.getLogger(OpenWeatherClient.class);

    public OpenWeatherClient(String apiKey) {
        this.apiKey = Objects.requireNonNull(apiKey, "API key must not be null");
        // Executor for asynchronous operations.  Cached thread pool allows
        // scaling threads up and down based on usage.
        this.asyncExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "weather-client-async");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public WeatherResponse fetchWeather(WeatherRequest request) throws WeatherSDKException {
        return requestWeather(request);
    }

    @Override
    public CompletableFuture<WeatherResponse> fetchWeatherAsync(WeatherRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return requestWeather(request);
            } catch (WeatherSDKException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Performs the actual HTTP call and parses the response.  Both
     * synchronous and asynchronous methods delegate to this helper.
     */
    private WeatherResponse requestWeather(WeatherRequest request) throws WeatherSDKException {
        try {
            String queryParams;
            if (request.getCityName() != null) {
                String encodedCity = URLEncoder.encode(request.getCityName(), StandardCharsets.UTF_8);
                queryParams = "q=" + encodedCity;
            } else {
                queryParams = "lat=" + request.getLatitude() + "&lon=" + request.getLongitude();
            }
            queryParams += "&appid=" + apiKey;
            queryParams += "&units=" + request.getUnits().getApiValue();
            if (request.getLanguage() != null && !request.getLanguage().isBlank()) {
                queryParams += "&lang=" + URLEncoder.encode(request.getLanguage(), StandardCharsets.UTF_8);
            }
            URL url = new URL(BASE_URL + "?" + queryParams);
            log.debug("Calling OpenWeather API: {}", url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int status = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            reader.close();
            String json = body.toString();
            if (status >= 200 && status < 300) {
                return parseResponse(json);
            }
            // API error: parse message if available
            try {
                JSONObject obj = new JSONObject(json);
                String msg = obj.optString("message", "Unknown error");
                log.warn("API error ({}): {}", status, msg);
                throw new WeatherSDKException("API error: " + msg + " (HTTP " + status + ")");
            } catch (JSONException e) {
                log.warn("API error: HTTP {}", status);
                throw new WeatherSDKException("API error: HTTP " + status);
            }
        } catch (MalformedURLException e) {
            throw new WeatherSDKException("Invalid URL for API request", e);
        } catch (IOException e) {
            throw new WeatherSDKException("Failed to communicate with OpenWeather API", e);
        }
    }

    /**
     * Parses the JSON response from OpenWeather into a simplified
     * {@link WeatherResponse}.  Only fields required by the SDK are extracted.
     */
    private WeatherResponse parseResponse(String json) throws WeatherSDKException {
        try {
            JSONObject root = new JSONObject(json);
            // Weather array
            JSONArray wArr = root.getJSONArray("weather");
            JSONObject wObj = wArr.getJSONObject(0);
            String main = wObj.optString("main", "");
            String desc = wObj.optString("description", "");
            WeatherResponse.Weather weather = new WeatherResponse.Weather(main, desc);
            // Main metrics
            JSONObject mObj = root.getJSONObject("main");
            double temp = mObj.optDouble("temp", Double.NaN);
            double feels = mObj.optDouble("feels_like", Double.NaN);
            WeatherResponse.Temperature tempGroup = new WeatherResponse.Temperature(temp, feels);
            int vis = root.optInt("visibility", 0);
            // Wind
            JSONObject windObj = root.optJSONObject("wind");
            double speed = windObj != null ? windObj.optDouble("speed", 0.0) : 0.0;
            WeatherResponse.Wind wind = new WeatherResponse.Wind(speed);
            long dt = root.optLong("dt", 0L);
            JSONObject sysObj = root.optJSONObject("sys");
            long sunrise = 0L;
            long sunset = 0L;
            if (sysObj != null) {
                sunrise = sysObj.optLong("sunrise", 0L);
                sunset = sysObj.optLong("sunset", 0L);
            }
            WeatherResponse.Sys sys = new WeatherResponse.Sys(sunrise, sunset);
            int timezone = root.optInt("timezone", 0);
            String name = root.optString("name", "");
            return new WeatherResponse(weather, tempGroup, vis, wind, dt, sys, timezone, name);
        } catch (JSONException e) {
            throw new WeatherSDKException("Failed to parse API response", e);
        }
    }
}