-- V1: Create hourly_prices table
-- Stores one row per hour per zone with the Nord Pool wholesale price.

CREATE TABLE hourly_prices (
    id            UUID                        NOT NULL DEFAULT gen_random_uuid(),
    hour_start    TIMESTAMP WITH TIME ZONE    NOT NULL,
    zone          VARCHAR(4)                  NOT NULL,
    price_eur_mwh NUMERIC(10, 4)              NOT NULL,
    price_with_vat NUMERIC(10, 6)             NOT NULL,
    fetched_at    TIMESTAMP WITH TIME ZONE    NOT NULL,

    CONSTRAINT pk_hourly_prices PRIMARY KEY (id),
    CONSTRAINT uq_hourly_prices_hour_zone UNIQUE (hour_start, zone),
    CONSTRAINT chk_hourly_prices_zone CHECK (zone IN ('EE', 'FI', 'LV', 'LT'))
);

-- Index for the most common query pattern: look up prices by zone + time range
CREATE INDEX idx_hourly_prices_zone_hour ON hourly_prices (zone, hour_start);

-- Index for time-only queries (e.g. "all zones at this hour")
CREATE INDEX idx_hourly_prices_hour_start ON hourly_prices (hour_start);

COMMENT ON TABLE hourly_prices IS
    'Nord Pool day-ahead electricity prices fetched from Elering API, one row per hour per Baltic zone.';
COMMENT ON COLUMN hourly_prices.hour_start IS
    'UTC start of the priced hour. E.g. 2025-01-15 11:00:00+00 covers 11:00-12:00 UTC.';
COMMENT ON COLUMN hourly_prices.zone IS
    'Baltic pricing zone: EE=Estonia, FI=Finland, LV=Latvia, LT=Lithuania.';
COMMENT ON COLUMN hourly_prices.price_eur_mwh IS
    'Raw Nord Pool wholesale price in EUR/MWh.';
COMMENT ON COLUMN hourly_prices.price_with_vat IS
    'Consumer price in EUR/kWh including Estonian VAT at 22%: price_eur_mwh / 1000 * 1.22';
