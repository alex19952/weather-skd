package com.example.weathersdk;

import com.example.weathersdk.impl.OpenWeatherService;
// intentionally not importing WeatherService here; the facade delegates to OpenWeatherService
import com.example.weathersdk.model.Units;
import com.example.weathersdk.model.WeatherRequest;
import com.example.weathersdk.serialization.WeatherJsonSerializer;
// no direct cache usage in this facade
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import org.json.JSONObject;

/**
 * Primary entry point to the weather SDK.  This class exposes a high‑level
 * API for retrieving simplified weather information from OpenWeather.  It
 * supports caching, two operational modes (on‑demand and polling) and
 * enforces a singleton pattern per API key to prevent accidental
 * overuse of the remote service.
 */
public class WeatherSDK {

    // Delegate service that performs the heavy lifting.  For backward
    // compatibility the legacy WeatherSDK wraps the new, more configurable
    // OpenWeatherService.  By default the service uses metric units and
    // English language.
    private final OpenWeatherService service;
    private final String apiKey;
    private final WeatherMode mode;

    private WeatherSDK(String apiKey, WeatherMode mode) {
        this.apiKey = apiKey;
        this.mode = mode;
        // Build an OpenWeatherService with default cache settings (10 entries, 10 min TTL).
        OpenWeatherService.Builder builder = new OpenWeatherService.Builder(apiKey)
                .mode(mode)
                .cacheSize(10)
                .ttl(Duration.ofMinutes(10))
                .pollIntervalMinutes(5);
        this.service = builder.build();
    }

    /**
     * Returns the singleton SDK instance for the specified API key.  If an
     * instance does not yet exist, it will be created.  If an instance already
     * exists, the mode argument is ignored and the existing instance is
     * returned.  This ensures that only one SDK object is created per key.
     *
     * @param apiKey OpenWeather API key
     * @param mode   desired operational mode
     * @return the corresponding SDK instance
     */
    public static synchronized WeatherSDK getInstance(String apiKey, WeatherMode mode) {
        // Delegate to the underlying service singleton to avoid duplicate instances.
        WeatherSDK existing = Holder.INSTANCES.get(apiKey);
        if (existing != null) {
            return existing;
        }
        WeatherSDK sdk = new WeatherSDK(apiKey, mode);
        Holder.INSTANCES.put(apiKey, sdk);
        return sdk;
    }

    /**
     * Removes and shuts down the SDK instance associated with the given API key.
     * The internal scheduler is stopped and cached data is discarded.  After
     * destruction, a new instance may be created for the same key using
     * {@link #getInstance(String, WeatherMode)}.
     *
     * @param apiKey the API key whose instance should be destroyed
     */
    public static synchronized void destroyInstance(String apiKey) {
        WeatherSDK sdk = Holder.INSTANCES.remove(apiKey);
        if (sdk != null) {
            sdk.service.shutdown();
        }
    }

    /**
     * Retrieves current weather information and converts it to the documented JSON structure.
     *
     * @param cityName name of the city (e.g., "London")
     * @return weather information serialized as a {@link JSONObject}
     * @throws WeatherSDKException if the API call fails or the city is not found
     */
    public JSONObject getCurrentWeatherJson(String cityName) throws WeatherSDKException {
        WeatherRequest req = new WeatherRequest.Builder()
                .city(cityName)
                .units(Units.METRIC)
                .language("en")
                .build();
        return WeatherJsonSerializer.toJson(service.getWeather(req));
    }

    /**
     * Retrieves current weather asynchronously as JSON.  Useful in reactive or GUI
     * applications where blocking the thread is undesirable.  The returned
     * future completes exceptionally with {@link WeatherSDKException} if the
     * lookup fails.
     *
     * @param cityName the city for which to retrieve the weather
     * @return a future containing the JSON representation of the weather
     */
    public CompletableFuture<JSONObject> getCurrentWeatherJsonAsync(String cityName) {
        WeatherRequest req = new WeatherRequest.Builder()
                .city(cityName)
                .units(Units.METRIC)
                .language("en")
                .build();
        return service.getWeatherAsync(req).thenApply(WeatherJsonSerializer::toJson);
    }

    // Holder class for lazy, thread‑safe instantiation of the map.  Avoids
    // classloader issues with double‑checked locking.
    private static class Holder {
        private static final Map<String, WeatherSDK> INSTANCES = new ConcurrentHashMap<>();
    }


}