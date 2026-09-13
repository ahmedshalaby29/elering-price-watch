package com.elering.pricewatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Application-level Spring beans: HTTP client, etc.
 */
@Configuration
public class AppConfig {

    @Value("${elering.api.base-url}")
    private String eleringBaseUrl;

    @Value("${elering.api.timeout-seconds}")
    private long timeoutSeconds;

    /**
     * Dedicated {@link RestClient} for calls to the Elering API.
     *
     * <p>Connect + read timeouts are wired from application.yml so they can be
     * overridden per environment via environment variables.
     */
    @Bean("eleringRestClient")
    public RestClient eleringRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        return RestClient.builder()
                .baseUrl(eleringBaseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "EleringPriceWatch/1.0")
                .requestFactory(factory)
                .build();
    }

    /**
     * Generic {@link RestClient} for outbound webhook/Telegram calls.
     */
    @Bean("notificationRestClient")
    public RestClient notificationRestClient(
            @Value("${notifications.webhook.connect-timeout-ms}") long connectMs,
            @Value("${notifications.webhook.read-timeout-ms}") long readMs) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectMs));
        factory.setReadTimeout(Duration.ofMillis(readMs));

        return RestClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "EleringPriceWatch/1.0")
                .requestFactory(factory)
                .build();
    }
}
