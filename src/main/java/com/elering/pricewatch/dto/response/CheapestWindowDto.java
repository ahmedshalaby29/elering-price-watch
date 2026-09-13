package com.elering.pricewatch.dto.response;

import com.elering.pricewatch.domain.enums.Zone;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for the cheapest-hours query.
 *
 * <p>Contains the selected hours and summary cost figures so the caller
 * can immediately decide whether to start an appliance or EV charger.
 */
@Value
@Builder
@Schema(description = "Result of the cheapest N-hours query")
public class CheapestWindowDto {

    @Schema(description = "Baltic pricing zone queried", example = "EE")
    Zone zone;

    @Schema(description = "Number of hours requested", example = "3")
    int requestedHours;

    @Schema(description = "Whether contiguous hours were requested", example = "false")
    boolean contiguous;

    @Schema(description = "Sum of the N cheapest hours' prices in EUR/MWh", example = "210.45")
    BigDecimal totalCostEurMwh;

    @Schema(description = "Average price of the selected hours in EUR/MWh", example = "70.15")
    BigDecimal avgPriceEurMwh;

    @Schema(description = "Estimated consumer cost in EUR/kWh (incl. 22% VAT) per kWh consumed",
            example = "0.085583")
    BigDecimal estimatedConsumerPriceEurKwh;

    @Schema(description = "The selected cheapest hours, ordered by time")
    List<HourlyPriceDto> hours;
}
