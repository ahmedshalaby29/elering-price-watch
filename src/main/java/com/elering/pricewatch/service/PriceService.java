package com.elering.pricewatch.service;

import com.elering.pricewatch.domain.entity.HourlyPrice;
import com.elering.pricewatch.domain.enums.Zone;
import com.elering.pricewatch.dto.response.CheapestWindowDto;
import com.elering.pricewatch.dto.response.HourlyPriceDto;
import com.elering.pricewatch.dto.response.PriceStatsDto;
import com.elering.pricewatch.exception.PriceNotFoundException;
import com.elering.pricewatch.mapper.HourlyPriceMapper;
import com.elering.pricewatch.repository.HourlyPriceRepository;
import com.elering.pricewatch.repository.projection.PriceStatsProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Core service for querying and analysing electricity prices.
 *
 * <h2>Cheapest-window algorithm</h2>
 * <p>Two modes are supported:
 * <ul>
 *   <li><b>Non-contiguous</b> – sort all hours by price ascending, take cheapest N.
 *       Ideal for flexible loads (e.g. "I need 3 kWh total at some point today").</li>
 *   <li><b>Contiguous</b> – sliding-window O(n) algorithm over the 24-hour price array.
 *       Ideal for loads that must run uninterrupted (e.g. "I need 3 consecutive hours
 *       for my dishwasher cycle").</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceService {

    private static final ZoneId TALLINN_TZ = ZoneId.of("Europe/Tallinn");

    private final HourlyPriceRepository priceRepository;
    private final HourlyPriceMapper priceMapper;

    // ── Public query methods ─────────────────────────────────────────────────────

    /**
     * Returns today's 24 hourly prices for the given zone (in Europe/Tallinn calendar day).
     *
     * @param zone the Baltic pricing zone
     * @return list of up to 24 hourly prices, ordered by hour ascending
     * @throws PriceNotFoundException if no prices are stored for today
     */
    public List<HourlyPriceDto> getTodayPrices(Zone zone) {
        LocalDate today = LocalDate.now(TALLINN_TZ);
        return getPricesForDate(zone, today);
    }

    /**
     * Returns tomorrow's 24 hourly prices for the given zone (in Europe/Tallinn calendar day).
     *
     * @param zone the Baltic pricing zone
     * @return list of hourly prices, ordered by hour ascending
     * @throws PriceNotFoundException if tomorrow's prices have not been published yet
     */
    public List<HourlyPriceDto> getTomorrowPrices(Zone zone) {
        LocalDate tomorrow = LocalDate.now(TALLINN_TZ).plusDays(1);
        return getPricesForDate(zone, tomorrow);
    }

    /**
     * Public accessor for a specific calendar date — used by the compare endpoint.
     */
    public List<HourlyPriceDto> getPricesForDatePublic(Zone zone, LocalDate date) {
        return getPricesForDate(zone, date);
    }

    /**
     * Returns prices for a specific calendar date (convenience for both today/tomorrow logic).
     */
    private List<HourlyPriceDto> getPricesForDate(Zone zone, LocalDate date) {
        OffsetDateTime from = date.atStartOfDay(TALLINN_TZ).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime to = from.plusDays(1);

        List<HourlyPrice> prices = priceRepository
                .findByZoneAndHourStartGreaterThanEqualAndHourStartLessThanOrderByHourStartAsc(zone, from, to);

        if (prices.isEmpty()) {
            throw new PriceNotFoundException(
                    "No prices found for zone=" + zone + " on date=" + date +
                    ". The Elering API may not have published them yet.");
        }
        return priceMapper.toDtoList(prices);
    }

    /**
     * Finds the cheapest N hours within a given calendar day for the specified zone.
     *
     * @param zone       the Baltic pricing zone
     * @param n          number of hours requested (1–24)
     * @param contiguous if true, the N hours must be consecutive (sliding window);
     *                   if false, the N cheapest individual hours are selected
     * @param date       the calendar day to search within (defaults to today if null)
     * @return a {@link CheapestWindowDto} containing the selected hours and cost summary
     * @throws PriceNotFoundException      if no prices are available for the given day
     * @throws IllegalArgumentException    if n < 1 or n > 24
     */
    public CheapestWindowDto getCheapestHours(Zone zone, int n, boolean contiguous, LocalDate date) {
        if (n < 1 || n > 24) {
            throw new IllegalArgumentException("n must be between 1 and 24, got: " + n);
        }

        LocalDate targetDate = (date != null) ? date : LocalDate.now(TALLINN_TZ);
        OffsetDateTime from = targetDate.atStartOfDay(TALLINN_TZ)
                .toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime to = from.plusDays(1);

        List<HourlyPrice> allPrices = priceRepository
                .findByZoneAndHourStartGreaterThanEqualAndHourStartLessThanOrderByHourStartAsc(zone, from, to);

        if (allPrices.isEmpty()) {
            throw new PriceNotFoundException(
                    "No prices found for zone=" + zone + " on date=" + targetDate);
        }
        if (n > allPrices.size()) {
            throw new IllegalArgumentException(
                    "Requested " + n + " hours but only " + allPrices.size() + " are available");
        }

        List<HourlyPrice> selected = contiguous
                ? findCheapestContiguous(allPrices, n)
                : findCheapestNonContiguous(allPrices, n);

        // Sort the selected hours chronologically for the response
        selected.sort(Comparator.comparing(HourlyPrice::getHourStart));

        BigDecimal total = selected.stream()
                .map(HourlyPrice::getPriceEurMwh)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = total.divide(BigDecimal.valueOf(selected.size()), 4, RoundingMode.HALF_UP);
        BigDecimal consumerAvg = avg.divide(HourlyPrice.MWH_TO_KWH, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.ONE.add(HourlyPrice.VAT_RATE))
                .setScale(6, RoundingMode.HALF_UP);

        return CheapestWindowDto.builder()
                .zone(zone)
                .requestedHours(n)
                .contiguous(contiguous)
                .totalCostEurMwh(total)
                .avgPriceEurMwh(avg)
                .estimatedConsumerPriceEurKwh(consumerAvg)
                .hours(priceMapper.toDtoList(selected))
                .build();
    }

    /**
     * Returns aggregated price statistics (min/max/avg) for a zone over a time range.
     *
     * @param zone the Baltic pricing zone
     * @param from inclusive range start (UTC)
     * @param to   exclusive range end (UTC)
     * @return {@link PriceStatsDto} with aggregated figures
     * @throws PriceNotFoundException if no data exists in the range
     */
    public PriceStatsDto getStats(Zone zone, OffsetDateTime from, OffsetDateTime to) {
        PriceStatsProjection stats = priceRepository.findStatsByZoneAndRange(zone, from, to)
                .filter(p -> p.getPriceCount() != null && p.getPriceCount() > 0)
                .orElseThrow(() -> new PriceNotFoundException(
                        "No price data found for zone=" + zone +
                        " in range [" + from + ", " + to + ")"));

        return PriceStatsDto.builder()
                .zone(zone)
                .from(from)
                .to(to)
                .minPrice(stats.getMinPrice())
                .maxPrice(stats.getMaxPrice())
                .avgPrice(stats.getAvgPrice() != null
                        ? stats.getAvgPrice().setScale(4, RoundingMode.HALF_UP)
                        : null)
                .priceCount(stats.getPriceCount())
                .build();
    }

    // ── Private algorithm implementations ────────────────────────────────────────

    /**
     * Finds the N cheapest individual (non-contiguous) hours by simple sort.
     *
     * <p>Time complexity: O(m log m) where m = number of hours in the day.
     *
     * @param prices list of all hourly prices for the day
     * @param n      number of hours to select
     * @return the N cheapest hours (unordered by time)
     */
    private List<HourlyPrice> findCheapestNonContiguous(List<HourlyPrice> prices, int n) {
        return prices.stream()
                .sorted(Comparator.comparing(HourlyPrice::getPriceEurMwh))
                .limit(n)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Finds the cheapest N <em>contiguous</em> hours using a sliding-window approach.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Compute the sum of the first N-hour window.</li>
     *   <li>Slide the window one position at a time, updating the running sum in O(1).</li>
     *   <li>Track the index of the minimum-sum window seen so far.</li>
     * </ol>
     * Time complexity: O(m) where m = number of hours in the day.
     *
     * @param prices chronologically ordered list of all hourly prices for the day
     * @param n      size of the contiguous window to find
     * @return the N contiguous hours forming the cheapest window
     */
    List<HourlyPrice> findCheapestContiguous(List<HourlyPrice> prices, int n) {
        int m = prices.size();

        // Compute sum of the initial window
        BigDecimal windowSum = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            windowSum = windowSum.add(prices.get(i).getPriceEurMwh());
        }

        BigDecimal minSum = windowSum;
        int minStart = 0;

        // Slide the window
        for (int i = n; i < m; i++) {
            windowSum = windowSum
                    .add(prices.get(i).getPriceEurMwh())
                    .subtract(prices.get(i - n).getPriceEurMwh());
            if (windowSum.compareTo(minSum) < 0) {
                minSum = windowSum;
                minStart = i - n + 1;
            }
        }

        return new ArrayList<>(prices.subList(minStart, minStart + n));
    }
}
