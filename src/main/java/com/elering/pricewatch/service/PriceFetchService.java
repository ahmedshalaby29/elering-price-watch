package com.elering.pricewatch.service;

import com.elering.pricewatch.client.EleringClient;
import com.elering.pricewatch.client.EleringPriceRecord;
import com.elering.pricewatch.domain.entity.HourlyPrice;
import com.elering.pricewatch.domain.enums.Zone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elering.pricewatch.repository.HourlyPriceRepository;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the fetch → persist → alert evaluation pipeline.
 *
 * <p>This service is invoked by the scheduler and by the manual admin trigger endpoint.
 * It is intentionally kept thin — business logic lives in {@link PriceService} and
 * {@link AlertService}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PriceFetchService {

    private final EleringClient eleringClient;
    private final HourlyPriceRepository priceRepository;
    private final AlertService alertService;

    @Value("${scheduling.zones}")
    private String zonesConfig;

    /**
     * Fetches and persists prices for the given date across all configured zones.
     *
     * <p>Uses an upsert strategy: if a record for (hourStart, zone) already exists,
     * its price is updated (in case Elering corrects data); otherwise a new record is inserted.
     *
     * @param date the date for which to fetch and persist prices
     * @return list of all newly-persisted or updated {@link HourlyPrice} entities
     */
    @Transactional
    public List<HourlyPrice> fetchAndPersist(LocalDate date) {
        log.info("Starting price fetch for date={}", date);
        List<HourlyPrice> allPersisted = new ArrayList<>();

        for (Zone zone : resolveZones()) {
            try {
                List<HourlyPrice> persisted = fetchAndPersistForZone(date, zone);
                allPersisted.addAll(persisted);
                alertService.evaluateAlerts(zone, persisted);
            } catch (Exception ex) {
                // Log but continue with other zones so one failure doesn't block all
                log.error("Failed to fetch prices for date={} zone={}: {}", date, zone, ex.getMessage(), ex);
            }
        }

        log.info("Price fetch complete for date={}. Total records persisted/updated: {}", date, allPersisted.size());
        return allPersisted;
    }

    /**
     * Fetches prices for a specific zone on a specific date and performs upserts.
     */
    private List<HourlyPrice> fetchAndPersistForZone(LocalDate date, Zone zone) {
        List<EleringPriceRecord> records = eleringClient.fetchPrices(date, zone);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<HourlyPrice> persisted = new ArrayList<>(records.size());
        for (EleringPriceRecord record : records) {
            HourlyPrice entity = priceRepository
                    .findByZoneAndHourStart(zone, record.hourStart())
                    .map(existing -> {
                        // Update existing record (price correction scenario)
                        existing.setPriceEurMwh(record.priceEurMwh());
                        existing.refreshDerivedFields();
                        existing.setFetchedAt(now);
                        return existing;
                    })
                    .orElseGet(() -> HourlyPrice.of(record.hourStart(), zone, record.priceEurMwh(), now));

            persisted.add(priceRepository.save(entity));
        }

        log.info("Persisted {} price records for date={} zone={}", persisted.size(), date, zone);
        return persisted;
    }

    /**
     * Parses the comma-separated zone config into a list of {@link Zone} enums.
     */
    private List<Zone> resolveZones() {
        List<Zone> zones = new ArrayList<>();
        for (String code : zonesConfig.split(",")) {
            try {
                zones.add(Zone.valueOf(code.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown zone in config: '{}', skipping", code.trim());
            }
        }
        return zones;
    }
}
