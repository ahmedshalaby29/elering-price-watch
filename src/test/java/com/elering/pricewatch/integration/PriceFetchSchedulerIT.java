package com.elering.pricewatch.integration;

import com.elering.pricewatch.domain.entity.HourlyPrice;
import com.elering.pricewatch.domain.enums.Zone;
import com.elering.pricewatch.repository.HourlyPriceRepository;
import com.elering.pricewatch.service.PriceFetchService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for the price fetch pipeline using WireMock to stub
 * the Elering API, and a real PostgreSQL database via Testcontainers.
 *
 * <p>This verifies the full chain:
 * EleringClientImpl → HTTP → WireMock → JSON parsing → upsert → repository.
 */
@DisplayName("PriceFetchService Integration Tests (WireMock + Testcontainers)")
class PriceFetchSchedulerIT extends AbstractIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private PriceFetchService priceFetchService;

    @Autowired
    private HourlyPriceRepository priceRepository;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(
                WireMockConfiguration.options().port(8089)
        );
        wireMockServer.start();
        configureFor("localhost", 8089);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void resetAndStub() {
        wireMockServer.resetAll();
        priceRepository.deleteAll();

        // Stub the EE zone endpoint
        stubFor(get(urlPathMatching("/nps/price"))
                .withQueryParam("fields", equalTo("ee"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("elering-response.json")));

        // Stub the FI zone endpoint
        stubFor(get(urlPathMatching("/nps/price"))
                .withQueryParam("fields", equalTo("fi"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("elering-response.json")));
    }

    @AfterEach
    void cleanupDb() {
        priceRepository.deleteAll();
    }

    @Test
    @DisplayName("fetchAndPersist writes 24 records per zone to Postgres")
    void fetchAndPersistWrites24RecordsPerZone() {
        LocalDate date = LocalDate.of(2025, 1, 15);
        List<HourlyPrice> result = priceFetchService.fetchAndPersist(date);

        // EE + FI = 48 records (24 each, since zones=EE,FI in test config)
        assertThat(result).hasSize(48);
        assertThat(priceRepository.count()).isEqualTo(48);
    }

    @Test
    @DisplayName("fetchAndPersist is idempotent — running twice doesn't duplicate records")
    void fetchAndPersistIsIdempotent() {
        LocalDate date = LocalDate.of(2025, 1, 15);

        priceFetchService.fetchAndPersist(date);
        priceFetchService.fetchAndPersist(date); // second call should upsert

        assertThat(priceRepository.count()).isEqualTo(48); // still 48, not 96
    }

    @Test
    @DisplayName("fetchAndPersist records have correct zone and price values")
    void fetchAndPersistRecordsHaveCorrectZone() {
        LocalDate date = LocalDate.of(2025, 1, 15);
        priceFetchService.fetchAndPersist(date);

        List<HourlyPrice> eePrices = priceRepository
                .findByZoneAndHourStartGreaterThanEqualAndHourStartLessThanOrderByHourStartAsc(
                        Zone.EE,
                        date.atStartOfDay().atOffset(java.time.ZoneOffset.UTC),
                        date.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC)
                );

        assertThat(eePrices).hasSize(24);
        // First hour price from the fixture: 89.35
        assertThat(eePrices.get(0).getPriceEurMwh()).isEqualByComparingTo("89.35");
        // Verify VAT-inclusive price is computed: 89.35 / 1000 * 1.22
        assertThat(eePrices.get(0).getPriceWithVat()).isNotNull().isPositive();
    }

    @Test
    @DisplayName("fetchAndPersist handles Elering API 5xx gracefully — continues other zones")
    void fetchAndPersistHandles5xxGracefully() {
        wireMockServer.resetAll();

        // Make EE return 500
        stubFor(get(urlPathMatching("/nps/price"))
                .withQueryParam("fields", equalTo("ee"))
                .willReturn(aResponse().withStatus(500)));

        // FI still returns OK
        stubFor(get(urlPathMatching("/nps/price"))
                .withQueryParam("fields", equalTo("fi"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("elering-response.json")));

        // Should not throw — EE failure is isolated
        assertThatNoException().isThrownBy(() ->
                priceFetchService.fetchAndPersist(LocalDate.of(2025, 1, 15)));

        // FI records still persisted despite EE failure
        assertThat(priceRepository.count()).isEqualTo(24);
    }
}
