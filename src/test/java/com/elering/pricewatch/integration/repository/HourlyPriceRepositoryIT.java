package com.elering.pricewatch.integration.repository;

import com.elering.pricewatch.domain.entity.HourlyPrice;
import com.elering.pricewatch.domain.enums.Zone;
import com.elering.pricewatch.integration.AbstractIntegrationTest;
import com.elering.pricewatch.repository.HourlyPriceRepository;
import com.elering.pricewatch.repository.projection.PriceStatsProjection;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link HourlyPriceRepository} against a real PostgreSQL instance.
 */
@DisplayName("HourlyPriceRepository Integration Tests")
class HourlyPriceRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private HourlyPriceRepository repository;

    private static final OffsetDateTime BASE = OffsetDateTime.of(
            2025, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void seedData() {
        repository.deleteAll();

        double[] prices = {
                89.35, 75.20, 60.10, 45.00, 38.50, 32.00,
                28.75, 25.50, 30.00, 55.00, 95.40, 120.60,
                135.25, 128.90, 115.75, 105.30, 98.45, 112.80,
                145.20, 168.50, 142.30, 118.90, 95.60, 78.40
        };

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (int i = 0; i < prices.length; i++) {
            repository.save(HourlyPrice.of(
                    BASE.plusHours(i),
                    Zone.EE,
                    BigDecimal.valueOf(prices[i]),
                    now
            ));
        }
    }

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("findByZoneAndHourStart returns the correct record")
    void findByZoneAndHourStartFindsExistingRecord() {
        Optional<HourlyPrice> found = repository.findByZoneAndHourStart(Zone.EE, BASE.plusHours(5));
        assertThat(found).isPresent();
        assertThat(found.get().getPriceEurMwh()).isEqualByComparingTo("32.00");
    }

    @Test
    @DisplayName("findByZoneAndHourStart returns empty for unknown hour")
    void findByZoneAndHourStartReturnsEmptyForUnknown() {
        Optional<HourlyPrice> found = repository.findByZoneAndHourStart(Zone.EE, BASE.plusHours(100));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByZone...OrderByHourStartAsc returns all 24 records in order")
    void findByZoneRangeReturnsOrderedPrices() {
        List<HourlyPrice> prices = repository
                .findByZoneAndHourStartGreaterThanEqualAndHourStartLessThanOrderByHourStartAsc(
                        Zone.EE, BASE, BASE.plusDays(1)
                );

        assertThat(prices).hasSize(24);
        // Verify chronological order
        for (int i = 1; i < prices.size(); i++) {
            assertThat(prices.get(i).getHourStart())
                    .isAfter(prices.get(i - 1).getHourStart());
        }
    }

    @Test
    @DisplayName("findStatsByZoneAndRange returns correct min, max, avg, count")
    void findStatsByZoneReturnsCorrectAggregates() {
        Optional<PriceStatsProjection> stats = repository.findStatsByZoneAndRange(
                Zone.EE, BASE, BASE.plusDays(1));

        assertThat(stats).isPresent();
        assertThat(stats.get().getPriceCount()).isEqualTo(24);
        assertThat(stats.get().getMinPrice()).isEqualByComparingTo("25.50");
        assertThat(stats.get().getMaxPrice()).isEqualByComparingTo("168.50");
        assertThat(stats.get().getAvgPrice()).isGreaterThan(BigDecimal.valueOf(25))
                .isLessThan(BigDecimal.valueOf(170));
    }

    @Test
    @DisplayName("Duplicate insert on (hourStart, zone) throws DataIntegrityViolationException")
    void duplicateInsertThrowsException() {
        HourlyPrice duplicate = HourlyPrice.of(
                BASE, Zone.EE, BigDecimal.valueOf(99.0), OffsetDateTime.now(ZoneOffset.UTC)
        );
        assertThatThrownBy(() -> {
            repository.save(duplicate);
            repository.flush();
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existsByZoneAndHourStartRange returns true when data exists")
    void existsByRangeReturnsTrueWhenDataPresent() {
        boolean exists = repository.existsByZoneAndHourStartGreaterThanEqualAndHourStartLessThan(
                Zone.EE, BASE, BASE.plusDays(1));
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByZoneAndHourStartRange returns false for different zone")
    void existsByRangeReturnsFalseForOtherZone() {
        boolean exists = repository.existsByZoneAndHourStartGreaterThanEqualAndHourStartLessThan(
                Zone.FI, BASE, BASE.plusDays(1));
        assertThat(exists).isFalse();
    }
}
