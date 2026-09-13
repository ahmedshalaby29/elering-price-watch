package com.elering.pricewatch.client;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Immutable value object representing a single hourly price entry returned by the
 * Elering API.
 *
 * @param hourStart  UTC start of the priced hour
 * @param priceEurMwh  wholesale Nord Pool price in EUR/MWh
 */
public record EleringPriceRecord(OffsetDateTime hourStart, BigDecimal priceEurMwh) {
}
