package com.elering.pricewatch.repository.projection;

import java.math.BigDecimal;

/**
 * JPQL interface projection for aggregated price statistics.
 *
 * <p>Spring Data JPA maps the JPQL aliases in the query to the getter names
 * of this interface (camelCase → alias matching).
 */
public interface PriceStatsProjection {

    BigDecimal getMinPrice();

    BigDecimal getMaxPrice();

    BigDecimal getAvgPrice();

    Long getPriceCount();
}
