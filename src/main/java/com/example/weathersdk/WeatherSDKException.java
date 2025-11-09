package com.example.weathersdk;

/**
 * Exception thrown by the SDK when an error occurs during API access or
 * internal processing.  Each exception contains a message that describes
 * the cause of the failure.  Clients are expected to catch and handle
 * this checked exception when using the SDK.
 */
public class WeatherSDKException extends Exception {

    /**
     * Creates a new exception with the specified message.
     *
     * @param message description of the failure
     */
    public WeatherSDKException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified message and underlying cause.
     *
     * @param message description of the failure
     * @param cause   the root cause
     */
    public WeatherSDKException(String message, Throwable cause) {
        super(message, cause);
    }
}