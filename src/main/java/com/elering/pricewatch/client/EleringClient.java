package com.elering.pricewatch.client;

import com.elering.pricewatch.domain.enums.Zone;

import java.time.LocalDate;
import java.util.List;

/**
 * Abstraction over the Elering NPS (Nord Pool Spot) price API.
 *
 * <p>Isolating the external API behind this interface means:
 * <ul>
 *   <li>Integration tests can stub it with WireMock or a simple in-memory implementation.</li>
 *   <li>The implementation can be swapped (e.g. for a different data provider) without
 *       touching service logic.</li>
 * </ul>
 */
public interface EleringClient {

    /**
     * Fetches hourly day-ahead prices for the specified date and zone.
     *
     * <p>The Elering API returns 23, 24, or 25 records depending on DST transitions.
     * The implementation normalises all timestamps to UTC.
     *
     * @param date the calendar date to fetch prices for
     * @param zone the Baltic pricing zone (EE/FI/LV/LT)
     * @return list of hourly price records, ordered by hour ascending
     * @throws com.elering.pricewatch.exception.EleringApiException if the API call fails
     *         after all retry attempts are exhausted
     */
    List<EleringPriceRecord> fetchPrices(LocalDate date, Zone zone);
}
