package com.elering.pricewatch.domain.entity;

import com.elering.pricewatch.domain.enums.Zone;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity representing one hour's electricity price for a specific Baltic zone.
 *
 * <p>Prices are stored in EUR/MWh as published by Nord Pool via the Elering API.
 * A derived consumer-facing price in EUR/kWh (including Estonian VAT at 22%) is
 * calculated and persisted so queries don't need to recompute it.
 *
 * <p>The combination of {@code hourStart} + {@code zone} is unique — attempting to
 * insert a duplicate will throw a constraint violation, which the service layer
 * handles via an upsert pattern.
 */
@Entity
@Table(
        name = "hourly_prices",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_hourly_prices_hour_zone",
                columnNames = {"hour_start", "zone"}
        ),
        indexes = {
                @Index(name = "idx_hourly_prices_zone_hour", columnList = "zone, hour_start"),
                @Index(name = "idx_hourly_prices_hour_start", columnList = "hour_start")
        }
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class HourlyPrice {

    /** Estonian VAT rate (22%). */
    public static final BigDecimal VAT_RATE = new BigDecimal("0.22");

    /** Conversion factor from EUR/MWh to EUR/kWh. */
    public static final BigDecimal MWH_TO_KWH = new BigDecimal("1000");

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * Start of the priced hour in UTC.
     * E.g. for EE 15:00 local time (UTC+2 in summer), this is stored as 13:00Z.
     */
    @Column(name = "hour_start", nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime hourStart;

    /** The Baltic pricing zone this record belongs to. */
    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false, length = 4)
    private Zone zone;

    /**
     * Raw Nord Pool price in EUR/MWh.
     * This is the wholesale market price before VAT or grid fees.
     */
    @Column(name = "price_eur_mwh", nullable = false,
            precision = 10, scale = 4)
    private BigDecimal priceEurMwh;

    /**
     * Consumer-facing price in EUR/kWh, including 22% Estonian VAT.
     * Formula: {@code priceEurMwh / 1000 * 1.22}
     */
    @Column(name = "price_with_vat", nullable = false,
            precision = 10, scale = 6)
    private BigDecimal priceWithVat;

    /** Timestamp when this record was fetched from the Elering API (UTC). */
    @Column(name = "fetched_at", nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime fetchedAt;

    /**
     * Convenience factory that computes the VAT-inclusive price automatically.
     *
     * @param hourStart   start of the priced hour in UTC
     * @param zone        pricing zone
     * @param priceEurMwh raw Nord Pool price in EUR/MWh
     * @param fetchedAt   when this record was retrieved
     * @return a fully-populated {@link HourlyPrice}
     */
    public static HourlyPrice of(OffsetDateTime hourStart, Zone zone,
                                  BigDecimal priceEurMwh, OffsetDateTime fetchedAt) {
        HourlyPrice hp = new HourlyPrice();
        hp.hourStart = hourStart;
        hp.zone = zone;
        hp.priceEurMwh = priceEurMwh;
        hp.priceWithVat = computePriceWithVat(priceEurMwh);
        hp.fetchedAt = fetchedAt;
        return hp;
    }

    /**
     * Recomputes and updates the VAT-inclusive price from the current raw price.
     * Call this after changing {@code priceEurMwh} in an upsert scenario.
     */
    public void refreshDerivedFields() {
        this.priceWithVat = computePriceWithVat(this.priceEurMwh);
    }

    private static BigDecimal computePriceWithVat(BigDecimal priceEurMwh) {
        return priceEurMwh
                .divide(MWH_TO_KWH, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.ONE.add(VAT_RATE))
                .setScale(6, RoundingMode.HALF_UP);
    }
}
