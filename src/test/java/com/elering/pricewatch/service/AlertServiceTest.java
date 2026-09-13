package com.elering.pricewatch.service;

import com.elering.pricewatch.domain.entity.AlertSubscription;
import com.elering.pricewatch.domain.entity.HourlyPrice;
import com.elering.pricewatch.domain.enums.AlertDirection;
import com.elering.pricewatch.domain.enums.NotificationChannel;
import com.elering.pricewatch.domain.enums.Zone;
import com.elering.pricewatch.repository.AlertSubscriptionRepository;
import com.elering.pricewatch.service.AlertService;
import com.elering.pricewatch.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AlertService} business logic — threshold evaluation
 * and cooldown logic tested in isolation.
 */
class AlertServiceTest {

    private AlertService alertService;

    @Mock
    private AlertSubscriptionRepository subscriptionRepository;

    @Mock
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        alertService = new AlertService(subscriptionRepository, notificationService);
    }

    // ── crossesThreshold ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("crossesThreshold()")
    class CrossesThreshold {

        @Test
        @DisplayName("BELOW: price < threshold → true")
        void belowThresholdTriggersAlert() {
            assertThat(alertService.crossesThreshold(
                    priceAt(49.0), subscriptionAt(50.0, AlertDirection.BELOW)
            )).isTrue();
        }

        @Test
        @DisplayName("BELOW: price == threshold → false")
        void atThresholdDoesNotTriggerBelowAlert() {
            assertThat(alertService.crossesThreshold(
                    priceAt(50.0), subscriptionAt(50.0, AlertDirection.BELOW)
            )).isFalse();
        }

        @Test
        @DisplayName("BELOW: price > threshold → false")
        void aboveThresholdDoesNotTriggerBelowAlert() {
            assertThat(alertService.crossesThreshold(
                    priceAt(51.0), subscriptionAt(50.0, AlertDirection.BELOW)
            )).isFalse();
        }

        @Test
        @DisplayName("ABOVE: price > threshold → true")
        void aboveThresholdTriggersAlert() {
            assertThat(alertService.crossesThreshold(
                    priceAt(51.0), subscriptionAt(50.0, AlertDirection.ABOVE)
            )).isTrue();
        }

        @Test
        @DisplayName("ABOVE: price == threshold → false")
        void atThresholdDoesNotTriggerAboveAlert() {
            assertThat(alertService.crossesThreshold(
                    priceAt(50.0), subscriptionAt(50.0, AlertDirection.ABOVE)
            )).isFalse();
        }

        @Test
        @DisplayName("Handles negative prices (BELOW threshold of 0)")
        void negativePricesBelowZeroThreshold() {
            assertThat(alertService.crossesThreshold(
                    priceAt(-10.0), subscriptionAt(0.0, AlertDirection.BELOW)
            )).isTrue();
        }
    }

    // ── isCooldownElapsed ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("isCooldownElapsed()")
    class IsCooldownElapsed {

        @Test
        @DisplayName("Never notified → cooldown considered elapsed")
        void neverNotifiedReturnTrue() {
            AlertSubscription sub = subscriptionAt(50.0, AlertDirection.BELOW);
            sub.setLastNotifiedAt(null);
            assertThat(alertService.isCooldownElapsed(sub)).isTrue();
        }

        @Test
        @DisplayName("Notified 25 hours ago → cooldown elapsed")
        void notified25HoursAgoReturnsTrue() {
            AlertSubscription sub = subscriptionAt(50.0, AlertDirection.BELOW);
            sub.setLastNotifiedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(25));
            assertThat(alertService.isCooldownElapsed(sub)).isTrue();
        }

        @Test
        @DisplayName("Notified 23 hours ago → cooldown not elapsed")
        void notified23HoursAgoReturnsFalse() {
            AlertSubscription sub = subscriptionAt(50.0, AlertDirection.BELOW);
            sub.setLastNotifiedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(23));
            assertThat(alertService.isCooldownElapsed(sub)).isFalse();
        }

        @Test
        @DisplayName("Notified just now → cooldown not elapsed")
        void notifiedJustNowReturnsFalse() {
            AlertSubscription sub = subscriptionAt(50.0, AlertDirection.BELOW);
            sub.setLastNotifiedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
            assertThat(alertService.isCooldownElapsed(sub)).isFalse();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HourlyPrice priceAt(double price) {
        return HourlyPrice.of(
                OffsetDateTime.now(ZoneOffset.UTC),
                Zone.EE,
                BigDecimal.valueOf(price),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private AlertSubscription subscriptionAt(double threshold, AlertDirection direction) {
        AlertSubscription sub = new AlertSubscription();
        sub.setThresholdEurMwh(BigDecimal.valueOf(threshold));
        sub.setDirection(direction);
        sub.setZone(Zone.EE);
        sub.setChannel(NotificationChannel.EMAIL);
        sub.setActive(true);
        sub.setLastNotifiedAt(null);
        return sub;
    }
}
