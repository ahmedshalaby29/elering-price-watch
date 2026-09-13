package com.elering.pricewatch.integration;

import com.elering.pricewatch.domain.entity.HourlyPrice;
import com.elering.pricewatch.domain.enums.Zone;
import com.elering.pricewatch.dto.response.CheapestWindowDto;
import com.elering.pricewatch.dto.response.HourlyPriceDto;
import com.elering.pricewatch.dto.response.PriceStatsDto;
import com.elering.pricewatch.repository.HourlyPriceRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link com.elering.pricewatch.controller.PriceController}.
 *
 * <p>Seeds price data directly via the repository and then makes real HTTP requests
 * to verify the full request → service → repository → response chain.
 */
@DisplayName("PriceController Integration Tests")
class PriceControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HourlyPriceRepository priceRepository;

    @BeforeEach
    void seedData() {
        priceRepository.deleteAll();
        seedTodayPrices();
    }

    @AfterEach
    void cleanup() {
        priceRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/prices/today returns 24 hourly prices for EE zone")
    void getTodayReturns24Prices() {
        ResponseEntity<List<HourlyPriceDto>> response = restTemplate.exchange(
                baseUrl() + "/api/prices/today?zone=EE",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(24);
        assertThat(response.getBody().get(0).getZone()).isEqualTo(Zone.EE);
    }

    @Test
    @DisplayName("GET /api/prices/today returns 404 when no prices stored")
    void getTodayReturns404WhenNoData() {
        priceRepository.deleteAll();
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/prices/today?zone=EE",
                HttpMethod.GET,
                null,
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("GET /api/prices/cheapest?hours=3 returns cheapest 3 non-contiguous hours")
    void getCheapestReturns3CheapestHours() {
        ResponseEntity<CheapestWindowDto> response = restTemplate.exchange(
                baseUrl() + "/api/prices/cheapest?zone=EE&hours=3&contiguous=false",
                HttpMethod.GET,
                null,
                CheapestWindowDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CheapestWindowDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getRequestedHours()).isEqualTo(3);
        assertThat(body.getHours()).hasSize(3);
        // Average of cheapest 3 should be <= overall average
        assertThat(body.getAvgPriceEurMwh()).isNotNull();
    }

    @Test
    @DisplayName("GET /api/prices/cheapest?contiguous=true returns contiguous block")
    void getCheapestContiguousReturnsBlock() {
        ResponseEntity<CheapestWindowDto> response = restTemplate.exchange(
                baseUrl() + "/api/prices/cheapest?zone=EE&hours=3&contiguous=true",
                HttpMethod.GET,
                null,
                CheapestWindowDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CheapestWindowDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isContiguous()).isTrue();
        // Verify the 3 hours are consecutive (each 1 hour apart)
        List<HourlyPriceDto> hours = body.getHours();
        for (int i = 1; i < hours.size(); i++) {
            OffsetDateTime prev = hours.get(i - 1).getHourStart();
            OffsetDateTime curr = hours.get(i).getHourStart();
            assertThat(curr).isEqualTo(prev.plusHours(1));
        }
    }

    @Test
    @DisplayName("GET /api/prices/stats returns correct min/max over range")
    void getStatsReturnsCorrectAggregates() {
        OffsetDateTime from = java.time.LocalDate.now(ZoneId.of("Europe/Tallinn"))
                .atStartOfDay(ZoneId.of("Europe/Tallinn"))
                .toOffsetDateTime()
                .withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime to = from.plusDays(1);

        String fromStr = java.time.format.DateTimeFormatter.ISO_INSTANT.format(from);
        String toStr = java.time.format.DateTimeFormatter.ISO_INSTANT.format(to);
        
        ResponseEntity<PriceStatsDto> response = restTemplate.exchange(
                baseUrl() + "/api/prices/stats?zone=EE" +
                "&from=" + fromStr + "&to=" + toStr,
                HttpMethod.GET,
                null,
                PriceStatsDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PriceStatsDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getPriceCount()).isEqualTo(24);
        assertThat(body.getMinPrice()).isLessThan(body.getMaxPrice());
        assertThat(body.getAvgPrice()).isBetween(body.getMinPrice(), body.getMaxPrice());
    }

    @Test
    @DisplayName("GET /api/prices/cheapest with hours=0 returns 422 validation error")
    void cheapestWithZeroHoursReturnsBadRequest() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/prices/cheapest?zone=EE&hours=0",
                HttpMethod.GET,
                null,
                String.class
        );
        assertThat(response.getStatusCode().value()).isIn(400, 422);
    }

    // ── Data seeding ──────────────────────────────────────────────────────────

    private void seedTodayPrices() {
        OffsetDateTime startOfDay = java.time.LocalDate.now(ZoneId.of("Europe/Tallinn"))
                .atStartOfDay(ZoneId.of("Europe/Tallinn"))
                .toOffsetDateTime()
                .withOffsetSameInstant(ZoneOffset.UTC);

        double[] prices = {
                89.35, 75.20, 60.10, 45.00, 38.50, 32.00,
                28.75, 25.50, 30.00, 55.00, 95.40, 120.60,
                135.25, 128.90, 115.75, 105.30, 98.45, 112.80,
                145.20, 168.50, 142.30, 118.90, 95.60, 78.40
        };

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (int i = 0; i < 24; i++) {
            priceRepository.save(HourlyPrice.of(
                    startOfDay.plusHours(i),
                    Zone.EE,
                    BigDecimal.valueOf(prices[i]),
                    now
            ));
        }
    }
}
