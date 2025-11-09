package com.example.weathersdk.impl;

import com.example.weathersdk.WeatherSDKException;
import com.example.weathersdk.api.WeatherService;
import com.example.weathersdk.cache.Cache;
import com.example.weathersdk.cache.LRUCache;
import com.example.weathersdk.client.OpenWeatherClient;
import com.example.weathersdk.client.WeatherClient;
import com.example.weathersdk.model.Units;
import com.example.weathersdk.model.WeatherRequest;
import com.example.weathersdk.model.WeatherResponse;
import com.example.weathersdk.WeatherMode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default service implementation that orchestrates caching, polling and
 * communication with the OpenWeather API.  Instances are created through
 * {@link Builder} and may be shared across threads.  There is exactly
 * one service instance per API key; requesting the same key returns the
 * same instance.
 */
public class OpenWeatherService implements WeatherService {

    /** Logger for diagnostic messages. */
    private static final Logger log = LoggerFactory.getLogger(OpenWeatherService.class);

    /**
     * Maintains one service instance per API key.  When a new builder
     * constructs a service with an already used key, the existing instance
     * is returned instead of creating a new one.
     */
    private static final Map<String, OpenWeatherService> instances = new ConcurrentHashMap<>();

    private final String apiKey;
    private final WeatherMode mode;
    private final Cache<String, CachedEntry> cache;
    private final Duration ttl;
    private final long pollIntervalMinutes;
    private final WeatherClient client;
    private ScheduledExecutorService poller;
    private boolean pollerOwned;
    private final ExecutorService asyncExecutor;

    /** Private constructor; use Builder to create instances. */
    private OpenWeatherService(Builder builder) {
        this.apiKey = builder.apiKey;
        this.mode = builder.mode;
        this.cache = builder.cache != null ? builder.cache : new LRUCache<>(builder.cacheSize);
        this.ttl = builder.ttl;
        this.pollIntervalMinutes = builder.pollIntervalMinutes;
        this.client = builder.client != null ? builder.client : new OpenWeatherClient(builder.apiKey);
        this.asyncExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "weather-service-async");
            t.setDaemon(true);
            return t;
        });
        if (mode == WeatherMode.POLLING) {
            startPolling();
        }
    }

    /**
     * Retrieves a service instance for the given API key.  Ensures singleton per key.
     */
    public static synchronized OpenWeatherService getInstance(Builder builder) {
        OpenWeatherService existing = instances.get(builder.apiKey);
        if (existing != null) {
            return existing;
        }
        OpenWeatherService service = new OpenWeatherService(builder);
        instances.put(builder.apiKey, service);
        return service;
    }

    @Override
    public WeatherResponse getWeather(WeatherRequest request) throws WeatherSDKException {
        String key = cacheKeyForRequest(request);
        // Check cache
        Optional<CachedEntry> cached = cache.get(key);
        if (cached.isPresent()) {
            CachedEntry entry = cached.get();
            if (Duration.between(entry.timestamp, Instant.now()).compareTo(ttl) < 0) {
                return entry.response;
            }
        }
        // Fetch from client
        WeatherResponse response = client.fetchWeather(request);
        cache.put(key, new CachedEntry(request, response, Instant.now()));
        return response;
    }

    @Override
    public CompletableFuture<WeatherResponse> getWeatherAsync(WeatherRequest request) {
        CompletableFuture<WeatherResponse> future = new CompletableFuture<>();
        try {
            asyncExecutor.submit(() -> {
                try {
                    WeatherResponse response = getWeather(request);
                    future.complete(response);
                } catch (WeatherSDKException e) {
                    future.completeExceptionally(e);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(new WeatherSDKException("Async executor is shut down", e));
        }
        return future;
    }

    @Override
    public void shutdown() {
        if (poller != null) {
            if (pollerOwned) {
                poller.shutdownNow();
            }
        }
        asyncExecutor.shutdownNow();
        cache.clear();
        instances.remove(apiKey);
    }

    /** Computes a unique cache key for a request based on city or coordinates and units/language. */
    private String cacheKeyForRequest(WeatherRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getCityName() != null) {
            sb.append(req.getCityName().toLowerCase());
        } else {
            sb.append(req.getLatitude()).append(',').append(req.getLongitude());
        }
        sb.append('|').append(req.getUnits().getApiValue());
        sb.append('|').append(req.getLanguage());
        return sb.toString();
    }

    /** Schedules background updates of all cached entries at fixed intervals. */
    private void startPolling() {
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "weather-service-poller");
            t.setDaemon(true);
            return t;
        });
        pollerOwned = true;
        poller.scheduleAtFixedRate(createPollingTask(), pollIntervalMinutes, pollIntervalMinutes, TimeUnit.MINUTES);
    }

    Runnable createPollingTask() {
        return () -> {
            List<String> keysSnapshot = new ArrayList<>();
            for (String key : cache.keys()) {
                keysSnapshot.add(key);
            }
            for (String key : keysSnapshot) {
                cache.get(key).ifPresent(entry -> {
                    WeatherRequest req = entry.request;
                    if (req == null) {
                        req = rebuildRequestFromKey(key);
                    }
                    try {
                        WeatherResponse resp = client.fetchWeather(req);
                        cache.put(key, new CachedEntry(req, resp, Instant.now()));
                    } catch (WeatherSDKException e) {
                        log.warn("Failed to refresh weather for key {}: {}", key, e.getMessage());
                    }
                });
            }
        };
    }

    /**
     * Legacy fallback used only when cached entries predate the request-aware structure.
     */
    private WeatherRequest rebuildRequestFromKey(String key) {
        String[] parts = key.split("\\|");
        WeatherRequest.Builder rb = new WeatherRequest.Builder();
        if (parts[0].contains(",")) {
            String[] coords = parts[0].split(",");
            rb.coordinates(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));
        } else {
            rb.city(parts[0]);
        }
        rb.units(Units.valueOf(parts[1].toUpperCase()));
        rb.language(parts[2]);
        return rb.build();
    }

    /**
     * Builder for constructing {@link OpenWeatherService} instances.  Supports
     * customisation of cache behaviour, polling, units and language.
     */
    public static class Builder {
        private final String apiKey;
        private WeatherMode mode = WeatherMode.ON_DEMAND;
        private int cacheSize = 10;
        private Duration ttl = Duration.ofMinutes(10);
        private long pollIntervalMinutes = 10;
        private Cache<String, CachedEntry> cache;
        private WeatherClient client;

        public Builder(String apiKey) {
            this.apiKey = Objects.requireNonNull(apiKey);
        }

        /** Sets the operational mode. */
        public Builder mode(WeatherMode mode) {
            this.mode = mode;
            return this;
        }

        /** Sets the maximum number of entries retained in the cache. */
        public Builder cacheSize(int size) {
            if (size < 1) {
                throw new IllegalArgumentException("Cache size must be at least 1");
            }
            this.cacheSize = Math.min(size, 10);
            return this;
        }

        /** Sets the time‑to‑live for cached entries. */
        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        /** Sets the polling interval in minutes.  Used only in polling mode. */
        public Builder pollIntervalMinutes(long minutes) {
            this.pollIntervalMinutes = minutes;
            return this;
        }

        /** Allows injecting a custom cache implementation (useful for testing). */
        public Builder cache(Cache<String, CachedEntry> cache) {
            this.cache = cache;
            return this;
        }

        /** Allows injecting a custom HTTP client implementation. */
        public Builder client(WeatherClient client) {
            this.client = client;
            return this;
        }

        /** Builds or retrieves the singleton service for the API key. */
        public OpenWeatherService build() {
            return OpenWeatherService.getInstance(this);
        }
    }

    /** Internal container for cached responses with timestamps. */
    private static class CachedEntry {
        final WeatherRequest request;
        final WeatherResponse response;
        final Instant timestamp;
        CachedEntry(WeatherRequest request, WeatherResponse response, Instant timestamp) {
            this.request = request;
            this.response = response;
            this.timestamp = timestamp;
        }
    }
}