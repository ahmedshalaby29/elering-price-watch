-- V2: Create alert_subscriptions table
-- Stores user-configured price threshold alerts with notification channel details.

CREATE TABLE alert_subscriptions (
    id                UUID                        NOT NULL DEFAULT gen_random_uuid(),
    email             VARCHAR(320),
    webhook_url       VARCHAR(2048),
    telegram_chat_id  VARCHAR(64),
    threshold_eur_mwh NUMERIC(10, 4)              NOT NULL,
    direction         VARCHAR(8)                  NOT NULL,
    zone              VARCHAR(4)                  NOT NULL,
    active            BOOLEAN                     NOT NULL DEFAULT TRUE,
    channel           VARCHAR(16)                 NOT NULL,
    label             VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    last_notified_at  TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_alert_subscriptions PRIMARY KEY (id),
    CONSTRAINT chk_alert_subscriptions_direction CHECK (direction IN ('BELOW', 'ABOVE')),
    CONSTRAINT chk_alert_subscriptions_zone CHECK (zone IN ('EE', 'FI', 'LV', 'LT')),
    CONSTRAINT chk_alert_subscriptions_channel CHECK (channel IN ('EMAIL', 'WEBHOOK', 'TELEGRAM'))
);

-- Index for the scheduler's per-fetch query: active subscriptions by zone
CREATE INDEX idx_alert_subscriptions_zone_active ON alert_subscriptions (zone, active)
    WHERE active = TRUE;

COMMENT ON TABLE alert_subscriptions IS
    'User-defined price alert subscriptions. Evaluated after each price fetch.';
COMMENT ON COLUMN alert_subscriptions.threshold_eur_mwh IS
    'Price threshold in EUR/MWh. Alert fires when price crosses this value.';
COMMENT ON COLUMN alert_subscriptions.direction IS
    'BELOW = alert when price < threshold; ABOVE = alert when price > threshold.';
COMMENT ON COLUMN alert_subscriptions.last_notified_at IS
    'Last time a notification was sent for this subscription. Used for 24-hour cooldown.';
