package com.example.weathersdk.model;

import java.util.Objects;

/**
 * Immutable representation of simplified weather data returned by the SDK.
 * <p>
 * The class exposes only the fields relevant for typical applications and
 * documented in the project brief.  Additional information from the
 * underlying OpenWeather response is intentionally omitted to keep the
 * interface minimal.  Consumers interested in other fields should
 * modify the SDK accordingly.
 */
public final class WeatherResponse {

    /** Group of basic weather parameters (Rain, Snow, Clouds etc.) and its description. */
    public static final class Weather {
        private final String main;
        private final String description;

        public Weather(String main, String description) {
            this.main = main;
            this.description = description;
        }

        /**
         * Returns the broad category of the current weather (e.g., "Clouds").
         *
         * @return the weather main field
         */
        public String getMain() {
            return main;
        }

        /**
         * Returns a detailed description of the current weather (e.g., "scattered clouds").
         *
         * @return the weather description
         */
        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return "Weather{" +
                    "main='" + main + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }
    }

    /** Group of temperature measurements. */
    public static final class Temperature {
        private final double temp;
        private final double feelsLike;

        public Temperature(double temp, double feelsLike) {
            this.temp = temp;
            this.feelsLike = feelsLike;
        }

        /**
         * Returns the current temperature in degrees Celsius (metric units by default).
         *
         * @return current temperature
         */
        public double getTemp() {
            return temp;
        }

        /**
         * Returns the perceived temperature accounting for wind chill and humidity.
         *
         * @return feels like temperature
         */
        public double getFeelsLike() {
            return feelsLike;
        }

        @Override
        public String toString() {
            return "Temperature{" +
                    "temp=" + temp +
                    ", feelsLike=" + feelsLike +
                    '}';
        }
    }

    /** Wind information. */
    public static final class Wind {
        private final double speed;

        public Wind(double speed) {
            this.speed = speed;
        }

        /**
         * Returns the wind speed in meters per second.
         *
         * @return wind speed
         */
        public double getSpeed() {
            return speed;
        }

        @Override
        public String toString() {
            return "Wind{" +
                    "speed=" + speed +
                    '}';
        }
    }

    /** Sunrise and sunset times as Unix timestamps. */
    public static final class Sys {
        private final long sunrise;
        private final long sunset;

        public Sys(long sunrise, long sunset) {
            this.sunrise = sunrise;
            this.sunset = sunset;
        }

        /**
         * Returns the sunrise time as a Unix timestamp.
         *
         * @return sunrise timestamp
         */
        public long getSunrise() {
            return sunrise;
        }

        /**
         * Returns the sunset time as a Unix timestamp.
         *
         * @return sunset timestamp
         */
        public long getSunset() {
            return sunset;
        }

        @Override
        public String toString() {
            return "Sys{" +
                    "sunrise=" + sunrise +
                    ", sunset=" + sunset +
                    '}';
        }
    }

    private final Weather weather;
    private final Temperature temperature;
    private final int visibility;
    private final Wind wind;
    private final long datetime;
    private final Sys sys;
    private final int timezone;
    private final String name;

    public WeatherResponse(Weather weather,
                           Temperature temperature,
                           int visibility,
                           Wind wind,
                           long datetime,
                           Sys sys,
                           int timezone,
                           String name) {
        this.weather = Objects.requireNonNull(weather);
        this.temperature = Objects.requireNonNull(temperature);
        this.visibility = visibility;
        this.wind = Objects.requireNonNull(wind);
        this.datetime = datetime;
        this.sys = Objects.requireNonNull(sys);
        this.timezone = timezone;
        this.name = Objects.requireNonNull(name);
    }

    /** Returns the top‑level weather information. */
    public Weather getWeather() {
        return weather;
    }

    /** Returns the temperature group. */
    public Temperature getTemperature() {
        return temperature;
    }

    /** Returns the visibility in metres. */
    public int getVisibility() {
        return visibility;
    }

    /** Returns the wind information. */
    public Wind getWind() {
        return wind;
    }

    /** Returns the data calculation time as a Unix timestamp. */
    public long getDatetime() {
        return datetime;
    }

    /** Returns sunrise and sunset times. */
    public Sys getSys() {
        return sys;
    }

    /** Returns the timezone offset in seconds from UTC. */
    public int getTimezone() {
        return timezone;
    }

    /** Returns the city name. */
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "WeatherResponse{" +
                "weather=" + weather +
                ", temperature=" + temperature +
                ", visibility=" + visibility +
                ", wind=" + wind +
                ", datetime=" + datetime +
                ", sys=" + sys +
                ", timezone=" + timezone +
                ", name='" + name + '\'' +
                '}';
    }
}