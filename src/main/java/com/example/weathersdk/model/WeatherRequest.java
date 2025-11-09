package com.example.weathersdk.model;

import java.util.Objects;

/**
 * Represents a request for weather information.  Clients can specify either a
 * city name or geographic coordinates.  Additional parameters include units
 * of measurement and desired language for descriptions.
 */
public class WeatherRequest {
    private final String cityName;
    private final Double latitude;
    private final Double longitude;
    private final Units units;
    private final String language;

    private WeatherRequest(Builder builder) {
        this.cityName = builder.cityName;
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.units = builder.units;
        this.language = builder.language;
    }

    /** Returns the name of the city, or {@code null} if coordinates are used. */
    public String getCityName() {
        return cityName;
    }

    /** Returns the latitude if coordinates are provided; otherwise {@code null}. */
    public Double getLatitude() {
        return latitude;
    }

    /** Returns the longitude if coordinates are provided; otherwise {@code null}. */
    public Double getLongitude() {
        return longitude;
    }

    /** Returns the measurement units for this request. */
    public Units getUnits() {
        return units;
    }

    /** Returns the language code for weather descriptions. */
    public String getLanguage() {
        return language;
    }

    /**
     * Builder for creating {@link WeatherRequest} instances.  Either a
     * city name or latitude/longitude pair must be supplied.
     */
    public static class Builder {
        private String cityName;
        private Double latitude;
        private Double longitude;
        private Units units = Units.METRIC;
        private String language = "en";

        /** Sets the name of the city to query. */
        public Builder city(String city) {
            this.cityName = city;
            return this;
        }

        /** Sets geographic coordinates to query. */
        public Builder coordinates(double lat, double lon) {
            this.latitude = lat;
            this.longitude = lon;
            return this;
        }

        /** Sets the measurement units. */
        public Builder units(Units units) {
            this.units = units;
            return this;
        }

        /** Sets the language of descriptions (ISO 639‑1 code). */
        public Builder language(String language) {
            this.language = language;
            return this;
        }

        /**
         * Builds the request.  Either city or coordinates must be supplied.
         *
         * @return a new immutable WeatherRequest
         */
        public WeatherRequest build() {
            if ((cityName == null || cityName.isBlank()) && (latitude == null || longitude == null)) {
                throw new IllegalStateException("Either city name or coordinates must be provided");
            }
            return new WeatherRequest(this);
        }
    }
}