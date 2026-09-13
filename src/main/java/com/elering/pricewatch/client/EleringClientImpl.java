package com.elering.pricewatch.client;

import com.elering.pricewatch.domain.enums.Zone;
import com.elering.pricewatch.exception.EleringApiException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Production implementation of {@link EleringClient} that calls the public
 * <a href="https://dashboard.elering.ee/api">Elering dashboard API</a>.
 *
 * <h2>API contract</h2>
 * <pre>
 * GET /nps/price?start={epochSeconds}&amp;end={epochSeconds}&amp;fields={zone}
 *
 * Response (example for EE):
 * {
 *   "success": true,
 *   "data": {
 *     "ee": [
 *       { "timestamp": 1705276800, "price": 89.35 },
 *       ...
 *     ]
 *   }
 * }
 * </pre>
 *
 * <h2>Resilience</h2>
 * Spring Retry wraps each call with up to 3 attempts and exponential backoff
 * (1 s, 2 s, 4 s). On final exhaustion, {@link #recover} translates the exception
 * to an {@link EleringApiException} with a descriptive message.
 */
@Component
@Slf4j
public class EleringClientImpl implements EleringClient {

    private final RestClient restClient;

    public EleringClientImpl(@Qualifier("eleringRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Value("${elering.api.retry-max-attempts}")
    private int maxAttempts;

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Retried up to {@code elering.api.retry-max-attempts} times with exponential
     * backoff on network errors or 5xx responses. 4xx errors are not retried.
     */
    @Override
    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            noRetryFor = {HttpClientErrorException.class},
            maxAttemptsExpression = "${elering.api.retry-max-attempts}",
            backoff = @Backoff(
                    delayExpression = "${elering.api.retry-backoff-ms}",
                    multiplier = 2.0,
                    maxDelay = 30_000
            )
    )
    public List<EleringPriceRecord> fetchPrices(LocalDate date, Zone zone) {
        String start = date.atStartOfDay().atOffset(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ISO_INSTANT);
        String end = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ISO_INSTANT);

        log.info("Fetching Elering prices: date={} zone={} start={} end={}", date, zone, start, end);

        EleringApiResponse response = restClient.get()
                .uri("/nps/price?start={start}&end={end}&fields={zone}",
                        start, end, zone.getApiCode())
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                    throw new HttpClientErrorException(HttpStatus.valueOf(res.getStatusCode().value()),
                            "Elering API client error: " + res.getStatusCode());
                })
                .onStatus(status -> status.is5xxServerError(), (req, res) -> {
                    throw new HttpServerErrorException(HttpStatus.valueOf(res.getStatusCode().value()),
                            "Elering API server error: " + res.getStatusCode());
                })
                .body(EleringApiResponse.class);

        if (response == null || !response.success() || response.data() == null) {
            throw new EleringApiException("Elering API returned unexpected response for zone " + zone);
        }

        List<EleringTimestampPrice> rawPrices = response.data().getOrDefault(zone.getApiCode(), List.of());

        if (rawPrices.isEmpty()) {
            log.warn("Elering API returned empty price list for date={} zone={}", date, zone);
        }

        List<EleringPriceRecord> records = new ArrayList<>(rawPrices.size());
        for (EleringTimestampPrice raw : rawPrices) {
            OffsetDateTime hourStart = Instant.ofEpochSecond(raw.timestamp())
                    .atOffset(ZoneOffset.UTC);
            records.add(new EleringPriceRecord(hourStart, BigDecimal.valueOf(raw.price())));
        }

        log.info("Fetched {} hourly prices for date={} zone={}", records.size(), date, zone);
        return records;
    }

    /**
     * Recovery method invoked when all retry attempts are exhausted.
     * Converts the low-level exception to a domain-level {@link EleringApiException}.
     */
    @Recover
    public List<EleringPriceRecord> recover(Exception ex, LocalDate date, Zone zone) {
        log.error("All retry attempts exhausted for date={} zone={}: {}", date, zone, ex.getMessage());
        throw new EleringApiException(
                "Failed to fetch prices from Elering API after retries: date=" + date +
                ", zone=" + zone + ". Cause: " + ex.getMessage(), ex);
    }

    // ── Internal JSON mapping records ────────────────────────────────────────────

    /**
     * Top-level Elering API response envelope.
     */
    record EleringApiResponse(
            boolean success,
            @JsonProperty("data") Map<String, List<EleringTimestampPrice>> data
    ) {}

    /**
     * Individual hourly price entry from the API.
     */
    record EleringTimestampPrice(
            long timestamp,
            double price
    ) {}
}
