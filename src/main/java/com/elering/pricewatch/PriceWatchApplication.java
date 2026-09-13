package com.elering.pricewatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Elering Price Watch — Spring Boot entry point.
 *
 * <p>Tracks Estonian Nord Pool day-ahead electricity prices via the public Elering API,
 * persists them in PostgreSQL, and alerts users when prices fall below or rise above
 * user-configured thresholds.
 */
@SpringBootApplication
@EnableScheduling
@EnableRetry
public class PriceWatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(PriceWatchApplication.class, args);
    }
}
