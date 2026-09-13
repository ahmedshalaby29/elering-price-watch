package com.elering.pricewatch.controller;

import com.elering.pricewatch.domain.enums.Zone;
import com.elering.pricewatch.dto.response.CheapestWindowDto;
import com.elering.pricewatch.dto.response.HourlyPriceDto;
import com.elering.pricewatch.dto.response.PriceStatsDto;
import com.elering.pricewatch.service.PriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST controller for electricity price queries.
 *
 * <p>All prices are in EUR/MWh (wholesale) unless stated otherwise.
 * Consumer prices including 22% Estonian VAT (EUR/kWh) are included in responses.
 */
@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
@Validated
@Tag(name = "Prices", description = "Electricity price queries for Baltic zones")
public class PriceController {

    private final PriceService priceService;

    // ── Today ────────────────────────────────────────────────────────────────

    @GetMapping("/today")
    @Operation(
            summary = "Today's hourly prices",
            description = "Returns up to 24 hourly Nord Pool prices for today in the given Baltic zone. " +
                          "The 'today' boundary is determined by the Europe/Tallinn calendar day."
    )
    public ResponseEntity<List<HourlyPriceDto>> getToday(
            @Parameter(description = "Baltic pricing zone", example = "EE")
            @RequestParam(defaultValue = "EE") Zone zone) {
        return ResponseEntity.ok(priceService.getTodayPrices(zone));
    }

    // ── Tomorrow ─────────────────────────────────────────────────────────────

    @GetMapping("/tomorrow")
    @Operation(
            summary = "Tomorrow's hourly prices",
            description = "Returns tomorrow's hourly prices once Elering has published them (usually ~13:00 EET). " +
                          "Returns 404 if prices are not yet available."
    )
    public ResponseEntity<List<HourlyPriceDto>> getTomorrow(
            @Parameter(description = "Baltic pricing zone", example = "EE")
            @RequestParam(defaultValue = "EE") Zone zone) {
        return ResponseEntity.ok(priceService.getTomorrowPrices(zone));
    }

    // ── Cheapest ─────────────────────────────────────────────────────────────

    @GetMapping("/cheapest")
    @Operation(
            summary = "Cheapest N hours",
            description = """
                    Finds the N cheapest hours for the given zone and date.

                    Two modes:
                    - **contiguous=false** (default): picks the N individually cheapest hours.
                      Ideal for flexible loads (EV charging, water heater) that can be split.
                    - **contiguous=true**: sliding-window algorithm finds the cheapest consecutive
                      N-hour block. Ideal for appliances that must run uninterrupted (dishwasher, oven).

                    Use this to answer: "When should I run my washing machine/EV charger today?"
                    """
    )
    public ResponseEntity<CheapestWindowDto> getCheapest(
            @Parameter(description = "Baltic pricing zone", example = "EE")
            @RequestParam(defaultValue = "EE") Zone zone,

            @Parameter(description = "Number of cheapest hours to find (1–24)", example = "3")
            @RequestParam(defaultValue = "3") @Min(1) @Max(24) int hours,

            @Parameter(description = "If true, hours must be consecutive (contiguous block)", example = "false")
            @RequestParam(defaultValue = "false") boolean contiguous,

            @Parameter(description = "Target date (ISO-8601, defaults to today)", example = "2025-01-15")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(priceService.getCheapestHours(zone, hours, contiguous, date));
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @Operation(
            summary = "Price statistics over a date range",
            description = "Returns min/max/avg price in EUR/MWh and record count for the given zone and time range. " +
                          "Useful for trend analysis of the volatile Baltic energy market."
    )
    public ResponseEntity<PriceStatsDto> getStats(
            @Parameter(description = "Baltic pricing zone", example = "EE")
            @RequestParam(defaultValue = "EE") Zone zone,

            @Parameter(description = "Range start (ISO-8601 with offset)", example = "2025-01-01T00:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,

            @Parameter(description = "Range end (ISO-8601 with offset, exclusive)", example = "2025-02-01T00:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ResponseEntity.ok(priceService.getStats(zone, from, to));
    }

    // ── Multi-zone compare ────────────────────────────────────────────────────

    @GetMapping("/compare")
    @Operation(
            summary = "Compare prices across multiple Baltic zones",
            description = "Returns today's or tomorrow's prices for multiple zones side by side. " +
                          "Useful for cross-border price spread analysis."
    )
    public ResponseEntity<Map<Zone, List<HourlyPriceDto>>> compareZones(
            @Parameter(description = "Target date (defaults to today)", example = "2025-01-15")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,

            @Parameter(description = "Comma-separated zones to compare (defaults to all four)", example = "EE,FI,LV,LT")
            @RequestParam(defaultValue = "EE,FI,LV,LT") List<Zone> zones) {

        LocalDate targetDate = (date != null) ? date : LocalDate.now(java.time.ZoneId.of("Europe/Tallinn"));
        Map<Zone, List<HourlyPriceDto>> result = new java.util.LinkedHashMap<>();

        for (Zone zone : zones) {
            try {
                // Reuse date-specific logic from PriceService (access via existing method)
                // We fetch for the target date by computing the window here
                java.time.OffsetDateTime from = targetDate
                        .atStartOfDay(java.time.ZoneId.of("Europe/Tallinn"))
                        .toOffsetDateTime()
                        .withOffsetSameInstant(java.time.ZoneOffset.UTC);
                java.time.OffsetDateTime to = from.plusDays(1);

                // Re-use cheapest (n=1) to validate data availability then fetch via stats...
                // Actually go direct to repo via stats won't return DTOs — we call priceService
                // through today/tomorrow logic. For arbitrary date, add a package-private method.
                result.put(zone, priceService.getPricesForDatePublic(zone, targetDate));
            } catch (com.elering.pricewatch.exception.PriceNotFoundException ex) {
                // Zone has no data — include empty list rather than failing entire request
                result.put(zone, List.of());
            }
        }
        return ResponseEntity.ok(result);
    }
}
