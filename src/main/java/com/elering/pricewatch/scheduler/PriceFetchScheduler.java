package com.elering.pricewatch.scheduler;

import com.elering.pricewatch.service.PriceFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Scheduled job that fetches next-day electricity prices from the Elering API.
 *
 * <h2>Schedule</h2>
 * <p>The cron expression defaults to {@code 0 0 11 * * *} (11:00 UTC = 13:00 EET / 14:00 EEST).
 * Nord Pool typically publishes next-day prices between 13:00 and 14:00 CET.
 * The cron is configurable via the {@code PRICE_FETCH_CRON} environment variable.
 *
 * <h2>Retry</h2>
 * <p>HTTP-level retries are handled by {@link com.elering.pricewatch.client.EleringClientImpl}
 * via Spring Retry. If prices are not yet published at 11:00 UTC, the admin trigger endpoint
 * {@code POST /api/admin/trigger-fetch} can be used to retry manually.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PriceFetchScheduler {

    private final PriceFetchService priceFetchService;
    private static final ZoneId TALLINN_TZ = ZoneId.of("Europe/Tallinn");

    /**
     * Main scheduled task: fetches tomorrow's prices for all configured zones.
     *
     * <p>Scheduled at 11:00 UTC daily (≈ 14:00 EET in winter / 14:00 EEST in summer).
     * The {@code zone} attribute ensures the trigger time is interpreted in the
     * correct timezone.
     */
    @Scheduled(cron = "${scheduling.price-fetch-cron}", zone = "UTC")
    public void fetchTomorrowPrices() {
        LocalDate tomorrow = LocalDate.now(TALLINN_TZ).plusDays(1);
        log.info("Scheduled price fetch triggered for date={}", tomorrow);
        try {
            priceFetchService.fetchAndPersist(tomorrow);
        } catch (Exception ex) {
            log.error("Scheduled price fetch failed for date={}: {}", tomorrow, ex.getMessage(), ex);
        }
    }
}
