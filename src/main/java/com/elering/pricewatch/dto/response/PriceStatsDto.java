package com.elering.pricewatch.dto.response;

import com.elering.pricewatch.domain.enums.Zone;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Response DTO for price statistics over a date range.
 */
@Value
@Builder
@Schema(description = "Aggregated price statistics over a date/time range")
public class PriceStatsDto {

    @Schema(description = "Baltic pricing zone", example = "EE")
    Zone zone;

    @Schema(description = "Range start (inclusive)", example = "2025-01-01T00:00:00Z")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime from;

    @Schema(description = "Range end (exclusive)", example = "2025-02-01T00:00:00Z")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime to;

    @Schema(description = "Minimum price in EUR/MWh in the range", example = "12.50")
    BigDecimal minPrice;

    @Schema(description = "Maximum price in EUR/MWh in the range", example = "245.00")
    BigDecimal maxPrice;

    @Schema(description = "Average price in EUR/MWh in the range", example = "89.35")
    BigDecimal avgPrice;

    @Schema(description = "Number of hourly records in the range", example = "744")
    long priceCount;
}
