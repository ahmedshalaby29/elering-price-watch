package com.elering.pricewatch.dto.response;

import com.elering.pricewatch.domain.enums.Zone;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Response DTO representing one hour's electricity price.
 */
@Value
@Builder
@Schema(description = "One hour's electricity price for a Baltic zone")
public class HourlyPriceDto {

    @Schema(description = "UTC start of the priced hour", example = "2025-01-15T11:00:00Z")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime hourStart;

    @Schema(description = "Baltic pricing zone", example = "EE")
    Zone zone;

    @Schema(description = "Nord Pool wholesale price in EUR/MWh", example = "89.35")
    BigDecimal priceEurMwh;

    @Schema(description = "Consumer price in EUR/kWh including 22% Estonian VAT", example = "0.108967")
    BigDecimal priceWithVat;

    @Schema(description = "Human-readable local hour label (EET/EEST)", example = "13:00–14:00")
    String localHourLabel;

    @Schema(description = "When this price was fetched from Elering API", example = "2025-01-14T13:02:00Z")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime fetchedAt;
}
