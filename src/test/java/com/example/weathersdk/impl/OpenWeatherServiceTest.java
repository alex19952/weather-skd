package com.example.weathersdk.impl;

import com.example.weathersdk.WeatherMode;
import com.example.weathersdk.WeatherSDKException;
import com.example.weathersdk.client.WeatherClient;
import com.example.weathersdk.model.Units;
import com.example.weathersdk.model.WeatherRequest;
import com.example.weathersdk.model.WeatherResponse;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.weathersdk.serialization.WeatherJsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-style tests covering cache behaviour, polling refreshes and asynchronous
 * exception handling of {@link OpenWeatherService}.
 */
class OpenWeatherServiceTest {

    private OpenWeatherService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
    }

    /**
     * Ensures that on-demand mode triggers a fresh client fetch once the cache entry's TTL has
     * elapsed.
     */
    @Test
    void refreshesDataWhenTtlExpires() throws Exception {
        RecordingClient client = new RecordingClient();
        service = new OpenWeatherService.Builder(uniqueKey())
                .mode(WeatherMode.ON_DEMAND)
                .ttl(Duration.ofMillis(50))
                .client(client)
                .build();

        WeatherRequest request = requestForCity("Berlin");

        service.getWeather(request);
        Thread.sleep(120);
        service.getWeather(request);

        assertEquals(2, client.callsFor(request));
    }

    /**
     * Verifies that the cache evicts the least-recently-used entry when the configured size limit is
     * exceeded.
     */
    @Test
    void evictsEntriesBeyondCacheLimit() throws Exception {
        RecordingClient client = new RecordingClient();
        service = new OpenWeatherService.Builder(uniqueKey())
                .mode(WeatherMode.ON_DEMAND)
                .cacheSize(10)
                .client(client)
                .build();

        for (int i = 0; i < 11; i++) {
            WeatherRequest request = requestForCity("City-" + i);
            service.getWeather(request);
        }

        WeatherRequest firstCity = requestForCity("City-0");
        service.getWeather(firstCity);

        assertEquals(2, client.callsFor(firstCity));
    }

    /**
     * Confirms that cache sizes above the maximum allowed are clamped so that eviction still
     * happens correctly.
     */
    @Test
    void cacheSizeAboveLimitIsClamped() throws Exception {
        RecordingClient client = new RecordingClient();
        service = new OpenWeatherService.Builder(uniqueKey())
                .mode(WeatherMode.ON_DEMAND)
                .cacheSize(50)
                .client(client)
                .build();

        for (int i = 0; i < 12; i++) {
            WeatherRequest request = requestForCity("Clamp-" + i);
            service.getWeather(request);
        }

        WeatherRequest firstCity = requestForCity("Clamp-0");
        service.getWeather(firstCity);

        assertEquals(2, client.callsFor(firstCity));
    }

    /**
     * Validates that polling mode refreshes cached entries by re-fetching data for existing
     * requests.
     */
    @Test
    void pollingRefreshesCachedEntries() throws Exception {
        RecordingClient client = new RecordingClient();
        service = new OpenWeatherService.Builder(uniqueKey())
                .mode(WeatherMode.POLLING)
                .ttl(Duration.ofMinutes(10))
                .client(client)
                .build();

        WeatherRequest request = requestForCity("Paris");

        WeatherResponse initial = service.getWeather(request);
        assertEquals(1, client.callsFor(request));

        service.createPollingTask().run();

        WeatherResponse afterPoll = service.getWeather(request);

        assertEquals(2, client.callsFor(request));
        assertEquals(initial.getName() + "#poll", afterPoll.getName());
    }

    /**
     * Checks that asynchronous invocations surface {@link WeatherSDKException} instances in the
     * resulting {@link CompletableFuture}.
     */
    @Test
    void asyncPropagatesWeatherSdkException() {
        WeatherClient failingClient = new WeatherClient() {
            @Override
            public WeatherResponse fetchWeather(WeatherRequest request) throws WeatherSDKException {
                throw new WeatherSDKException("boom");
            }

            @Override
            public CompletableFuture<WeatherResponse> fetchWeatherAsync(WeatherRequest request) {
                CompletableFuture<WeatherResponse> future = new CompletableFuture<>();
                future.completeExceptionally(new WeatherSDKException("boom"));
                return future;
            }
        };

        service = new OpenWeatherService.Builder(uniqueKey())
                .mode(WeatherMode.ON_DEMAND)
                .client(failingClient)
                .build();

        WeatherRequest request = requestForCity("Rome");

        CompletableFuture<WeatherResponse> future = service.getWeatherAsync(request);
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertNotNull(exception.getCause());
        assertEquals(WeatherSDKException.class, exception.getCause().getClass());
    }

    /**
     * Guarantees that the JSON representation contains all required keys for downstream consumers.
     */
    @Test
    void jsonContainsRequiredKeys() throws Exception {
        WeatherClient failingClient = new WeatherClient() {
            @Override
            public WeatherResponse fetchWeather(WeatherRequest request) {
                return new WeatherResponse(
                        new WeatherResponse.Weather("Clouds", "scattered clouds"),
                        new WeatherResponse.Temperature(269.6, 267.57),
                        10000,
                        new WeatherResponse.Wind(1.38),
                        1675744800L,
                        new WeatherResponse.Sys(1675751262L, 1675787560L),
                        3600,
                        request.getCityName() != null ? request.getCityName() : "Zocca"
                );
            }

            @Override
            public CompletableFuture<WeatherResponse> fetchWeatherAsync(WeatherRequest r) {
                return CompletableFuture.completedFuture(fetchWeather(r));
            }
        };

        service = new OpenWeatherService.Builder(uniqueKey())
                .mode(WeatherMode.ON_DEMAND)
                .client(failingClient)
                .build();

        WeatherRequest testWeatherRequest = new WeatherRequest.Builder().city("Porto").build();
        JSONObject jsonObject = WeatherJsonSerializer.toJson(service.getWeather(testWeatherRequest));
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.valueToTree(jsonObject.toMap());
        assertHasAll(jsonNode,
                "/weather/main",
                "/weather/description",
                "/temperature/temp",
                "/temperature/feels_like",
                "/visibility",
                "/wind/speed",
                "/datetime",
                "/sys/sunrise",
                "/sys/sunset",
                "/timezone",
                "/name"
        );
    }

    private static WeatherRequest requestForCity(String city) {
        return new WeatherRequest.Builder()
                .city(city)
                .units(Units.METRIC)
                .language("en")
                .build();
    }

    static void assertHasAll(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode node = root.at(pointer); // JSON Pointer
            assertTrue(!node.isMissingNode() && !node.isNull(),
                    "missing: " + pointer);
        }
    }

    private static String uniqueKey() {
        return "test-key-" + UUID.randomUUID();
    }

    /** Simple client that tracks invocation counts and returns synthetic responses. */
    private static final class RecordingClient implements WeatherClient {
        private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

        @Override
        public WeatherResponse fetchWeather(WeatherRequest request) {
            String key = keyFor(request);
            int invocation = counts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
            return new WeatherResponse(
                    new WeatherResponse.Weather("Main", "Desc" + invocation),
                    new WeatherResponse.Temperature(10 + invocation, 9 + invocation),
                    1000,
                    new WeatherResponse.Wind(5 + invocation),
                    invocation,
                    new WeatherResponse.Sys(1, 2),
                    3600,
                    invocation == 1 ? request.getCityName() : request.getCityName() + "#poll"
            );
        }

        @Override
        public CompletableFuture<WeatherResponse> fetchWeatherAsync(WeatherRequest request) {
            return CompletableFuture.completedFuture(fetchWeather(request));
        }

        int callsFor(WeatherRequest request) {
            String key = keyFor(request);
            AtomicInteger counter = counts.get(key);
            return counter == null ? 0 : counter.get();
        }

        private String keyFor(WeatherRequest request) {
            return (request.getCityName() == null ? "" : request.getCityName().toLowerCase())
                    + "|" + request.getUnits().getApiValue()
                    + "|" + request.getLanguage();
        }
    }
}

