# Java SDK for Accessing OpenWeather API

## Overview

This project is a Java SDK that simplifies working with the [OpenWeather API](https://openweathermap.org/current). It allows developers to fetch current weather data by city name without worrying about constructing HTTP requests or parsing responses. The library supports two operating modes—**on‑demand** and **polling**—implements caching with limits on the number of cities and freshness of data, is thread‑safe and throws clear exceptions when errors occur.

> Below you'll find a detailed analysis of the response format, operating modes and cache limits, along with an English version of the guide.

## Features

* The public API consists only of the `WeatherSDK` facade, the `WeatherMode` enumeration and the `WeatherSDKException` exception. Results are always returned as `org.json.JSONObject`. All other packages are considered internal and may change without notice.
* A **single object** is created per API key (Singleton pattern). Creating a second object with the same key returns the existing instance. You can **destroy an instance** to free resources.
* Two modes of operation are supported: `POLLING` and `ON_DEMAND`.
* A `LinkedHashMap`‑based cache with an LRU policy stores up to 10 cities. Data is kept for a maximum of 10 minutes; when the capacity is exceeded the oldest entry is removed.
* Internally a **Builder** pattern is used to configure the service. This makes it easy to extend the SDK in the future.
* Transparent error handling: if a request fails or a city isn't found, the method throws a checked `WeatherSDKException` explaining the cause.
* JSON parsing uses the `org.json` library.
* A layered architecture (client, cache, service) simplifies maintenance.
* *Additional:* asynchronous requests via `CompletableFuture` are implemented for both modes.
* *Additional:* the SDK can also fetch weather by geographic coordinates.

## Operating Modes

Two modes are available depending on usage patterns:

- **ON_DEMAND** – Best for occasional or one‑off city queries. When called, the SDK checks the cache. If the data is fresh (younger than 10 minutes), it returns it immediately. If the data is stale or absent, it performs a network call and stores the result in the cache.
- **POLLING** – Best when the same cities are queried frequently and response speed is critical. An internal scheduler (`ScheduledExecutorService`) refreshes the cache every 10 minutes for all cities already present. Client calls to `getCurrentWeatherJson` return up‑to‑date data without network latency.

The mode is specified on initialization: `WeatherSDK.getInstance(apiKey, WeatherMode.POLLING)`. To switch modes you must destroy the current instance via `WeatherSDK.destroyInstance(apiKey)` and create a new one with the desired mode.

## Cache Limits

- **Maximum of 10 cities.** The cache is an LRU structure that automatically removes the oldest city when full.
- **Freshness of 10 minutes.** On each request the SDK checks the timestamp; if more than 10 minutes have passed it triggers an update.
- **Reset when destroying the instance.** Calling `WeatherSDK.destroyInstance(apiKey)` clears the cache and stops polling tasks.

## JSON Response Format

The SDK returns a ready `JSONObject` with the following structure:

```json
{
  "weather": {
    "main": "Clouds",
    "description": "scattered clouds"
  },
  "temperature": {
    "temp": 269.6,
    "feels_like": 267.57
  },
  "visibility": 10000,
  "wind": {
    "speed": 1.38
  },
  "datetime": 1675744800,
  "sys": {
    "sunrise": 1675751262,
    "sunset": 1675787560
  },
  "timezone": 3600,
  "name": "Zocca"
}
```

Each field is assembled inside the SDK based on the OpenWeather response. See the “Working with JSON” section for an example.

## Installation

The SDK is a Maven project. You can install it in two ways.

### Method 1: Build from source (Maven)

1. Install JDK 21 or newer (JDK < 21 is not guaranteed to work).
2. Clone the repository or download the source code.
3. Run:

```bash
mvn package -DskipTests
```

This produces `target/weather-sdk-1.0.0.jar`, which you can add to your project. Maven will also pull in additional dependencies automatically.

### Method 2: Download from the GitHub release

1. Go to the [release page](https://github.com/alex19952/weather-skd/releases).
2. Download the current artifact.
3. Add the JAR to your application’s classpath.

## Usage

Below is a minimal example showing how to create an instance, retrieve weather information and handle exceptions.

```java
import com.example.weathersdk.*;
import org.json.JSONObject;

public class WeatherExample {
  public static void main(String[] args) {
    // Your API key from OpenWeather
    String apiKey = "YOUR_API_KEY";
    // Create the SDK in on‑demand mode. If called again with the same key,
    // the existing object will be returned.
    WeatherSDK sdk = WeatherSDK.getInstance(apiKey, WeatherMode.ON_DEMAND);
    try {
      // Request weather for Moscow as JSON
      JSONObject json = sdk.getCurrentWeatherJson("Moscow");
      System.out.println(json.toString(2));
    } catch (WeatherSDKException e) {
      // Error handling: invalid key, city not found, network problems, etc.
      System.err.println("Unable to get weather: " + e.getMessage());
    }
    // Destroy the instance and free resources if needed
    WeatherSDK.destroyInstance(apiKey);
  }
}
```

### Asynchronous calls and advanced scenarios

The SDK supports asynchronous operations via `getCurrentWeatherJsonAsync`, which returns a `CompletableFuture<JSONObject>`. This allows you to obtain the same data as in the synchronous example without blocking.

Internal components (builder, clients, cache) are part of the implementation and may change. Direct use of these classes is not supported in order to keep the public API minimal.

## Project Structure

```
weather-sdk/
 ├── pom.xml                            # Maven build file with dependencies
 ├── README.md                          # this guide
 └── src/main/java/com/example/weathersdk/
     ├── api/
     │   └── WeatherService.java        # service interface
     ├── client/
     │   ├── WeatherClient.java         # HTTP client abstraction
     │   └── OpenWeatherClient.java     # HttpURLConnection implementation
     ├── cache/
     │   ├── Cache.java                 # cache abstraction
     │   └── LRUCache.java              # default LRU cache
     ├── impl/
     │   └── OpenWeatherService.java    # service implementation with polling and builder
     ├── model/
     │   ├── Units.java                 # measurement units (metric, imperial…)
     │   ├── WeatherRequest.java        # request object (city or coordinates)
     │   └── WeatherResponse.java       # response model broken into nested classes
     ├── serialization/
     │   └── WeatherJsonSerializer.java # converts WeatherResponse to JSON
     ├── WeatherMode.java               # enum of modes (on‑demand/polling)
     ├── WeatherSDK.java                # simplified facade wrapper
     └── WeatherSDKException.java       # checked exception type
```

Packages outside `com.example.weathersdk` and `com.example.weathersdk.model` are intended for internal use. Their API may change between versions and is not guaranteed to be stable for SDK consumers.