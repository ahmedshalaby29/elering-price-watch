package com.elering.pricewatch.repository;

import com.elering.pricewatch.domain.entity.HourlyPrice;
import com.elering.pricewatch.domain.enums.Zone;
import com.elering.pricewatch.repository.projection.PriceStatsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link HourlyPrice} entities.
 */
@Repository
public interface HourlyPriceRepository extends JpaRepository<HourlyPrice, UUID> {

    /**
     * Fetches all hourly prices for the given zone within the [from, to) window,
     * ordered chronologically.
     *
     * @param zone  the Baltic pricing zone
     * @param from  inclusive start (UTC)
     * @param to    exclusive end (UTC)
     * @return ordered list of hourly prices
     */
    List<HourlyPrice> findByZoneAndHourStartGreaterThanEqualAndHourStartLessThanOrderByHourStartAsc(
            Zone zone, OffsetDateTime from, OffsetDateTime to);

    /**
     * Finds the exact record for a specific zone + hour, if it exists.
     * Used by the upsert logic in the fetch service.
     *
     * @param zone      the Baltic pricing zone
     * @param hourStart the exact start of the hour (UTC)
     * @return the matching record, if any
     */
    Optional<HourlyPrice> findByZoneAndHourStart(Zone zone, OffsetDateTime hourStart);

    /**
     * Checks whether any prices for the given zone and date window are already stored.
     * Used to determine if tomorrow's prices have been published yet.
     *
     * @param zone the Baltic pricing zone
     * @param from inclusive window start (UTC)
     * @param to   exclusive window end (UTC)
     * @return true if at least one record exists in the window
     */
    boolean existsByZoneAndHourStartGreaterThanEqualAndHourStartLessThan(
            Zone zone, OffsetDateTime from, OffsetDateTime to);

    /**
     * Aggregates min, max, and average prices for a zone over a date range.
     * Returns a {@link PriceStatsProjection} interface projection populated by JPQL.
     *
     * @param zone the Baltic pricing zone
     * @param from inclusive range start (UTC)
     * @param to   exclusive range end (UTC)
     * @return a stats projection, or empty if no data in range
     */
    @Query("""
            SELECT MIN(hp.priceEurMwh) AS minPrice,
                   MAX(hp.priceEurMwh) AS maxPrice,
                   AVG(hp.priceEurMwh) AS avgPrice,
                   COUNT(hp)           AS priceCount
            FROM HourlyPrice hp
            WHERE hp.zone = :zone
              AND hp.hourStart >= :from
              AND hp.hourStart <  :to
            """)
    Optional<PriceStatsProjection> findStatsByZoneAndRange(
            @Param("zone") Zone zone,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /**
     * Deletes all records for a zone in the given window.
     * Used by the fetch service when re-fetching data for a day.
     *
     * @param zone the Baltic pricing zone
     * @param from inclusive window start (UTC)
     * @param to   exclusive window end (UTC)
     */
    void deleteByZoneAndHourStartGreaterThanEqualAndHourStartLessThan(
            Zone zone, OffsetDateTime from, OffsetDateTime to);
}
