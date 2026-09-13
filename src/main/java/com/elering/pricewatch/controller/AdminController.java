package com.elering.pricewatch.controller;

import com.elering.pricewatch.service.PriceFetchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * Admin-only endpoints for manual operations.
 *
 * <p>In production, secure these with Spring Security or an API gateway.
 * Currently they are open for demonstration purposes.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Manual trigger and admin operations")
public class AdminController {

    private final PriceFetchService priceFetchService;

    /**
     * Manually triggers a price fetch for a specific date.
     *
     * <p>Use this to:
     * <ul>
     *   <li>Re-fetch today's or tomorrow's prices if the scheduled job ran too early.</li>
     *   <li>Backfill historical prices for a specific date.</li>
     * </ul>
     */
    @PostMapping("/trigger-fetch")
    @Operation(
            summary = "Manually trigger a price fetch",
            description = "Fetches prices for the given date from the Elering API and persists them. " +
                          "Defaults to tomorrow if no date is provided. " +
                          "Useful when the scheduled fetch ran before Elering published prices."
    )
    public ResponseEntity<Map<String, Object>> triggerFetch(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now(ZoneId.of("Europe/Tallinn")).plusDays(1);
        log.info("Manual price fetch triggered for date={}", targetDate);

        int count = priceFetchService.fetchAndPersist(targetDate).size();

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "date", targetDate.toString(),
                "recordsPersisted", count
        ));
    }
}
