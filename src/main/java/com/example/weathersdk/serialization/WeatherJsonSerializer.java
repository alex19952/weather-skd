package com.example.weathersdk.serialization;

import com.example.weathersdk.model.WeatherResponse;
import java.util.Objects;
import org.json.JSONObject;

/**
 * Utility class that transforms {@link WeatherResponse} objects into the
 * JSON structure mandated by the SDK assignment.
 */
public final class WeatherJsonSerializer {

    private WeatherJsonSerializer() {
        // Utility class
    }

    /**
     * Converts the provided {@link WeatherResponse} into a {@link JSONObject}
     * that matches the documented SDK schema.
     *
     * @param response weather response returned by the SDK
     * @return JSON representation of the response
     */
    public static JSONObject toJson(WeatherResponse response) {
        Objects.requireNonNull(response, "response must not be null");

        JSONObject root = new JSONObject();
        WeatherResponse.Weather weather = response.getWeather();
        WeatherResponse.Temperature temperature = response.getTemperature();
        WeatherResponse.Wind wind = response.getWind();
        WeatherResponse.Sys sys = response.getSys();

        JSONObject weatherObj = new JSONObject()
                .put("main", weather.getMain())
                .put("description", weather.getDescription());
        JSONObject temperatureObj = new JSONObject()
                .put("temp", temperature.getTemp())
                .put("feels_like", temperature.getFeelsLike());
        JSONObject windObj = new JSONObject()
                .put("speed", wind.getSpeed());
        JSONObject sysObj = new JSONObject()
                .put("sunrise", sys.getSunrise())
                .put("sunset", sys.getSunset());

        return root
                .put("weather", weatherObj)
                .put("temperature", temperatureObj)
                .put("visibility", response.getVisibility())
                .put("wind", windObj)
                .put("datetime", response.getDatetime())
                .put("sys", sysObj)
                .put("timezone", response.getTimezone())
                .put("name", response.getName());
    }
}
