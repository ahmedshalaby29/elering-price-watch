package com.elering.pricewatch.exception;

/**
 * Thrown when a call to the Elering API fails after all retry attempts are exhausted,
 * or when the API returns an unexpected/invalid response.
 */
public class EleringApiException extends RuntimeException {

    public EleringApiException(String message) {
        super(message);
    }

    public EleringApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
