package com.elering.pricewatch.service;

import com.elering.pricewatch.domain.entity.AlertSubscription;
import com.elering.pricewatch.domain.entity.HourlyPrice;
import com.elering.pricewatch.domain.enums.Zone;
import com.elering.pricewatch.repository.AlertSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Evaluates active alert subscriptions against freshly-fetched prices and
 * dispatches notifications for triggered thresholds.
 *
 * <h2>Cooldown logic</h2>
 * <p>Each subscription has a 24-hour notification cooldown to prevent flooding.
 * A notification is sent only if {@code lastNotifiedAt} is null <em>or</em>
 * more than 24 hours in the past.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertService {

    private static final long COOLDOWN_HOURS = 24;

    private final AlertSubscriptionRepository subscriptionRepository;
    private final NotificationService notificationService;

    /**
     * Evaluates all active subscriptions for the given zone against the newly-fetched prices.
     *
     * <p>For each subscription, collects the hours that cross the threshold, applies the
     * cooldown check, and dispatches a notification if warranted.
     *
     * @param zone   the zone these prices belong to
     * @param prices the freshly fetched and persisted hourly prices
     */
    @Transactional
    public void evaluateAlerts(Zone zone, List<HourlyPrice> prices) {
        if (prices.isEmpty()) {
            return;
        }

        List<AlertSubscription> subscriptions = subscriptionRepository.findByZoneAndActiveTrue(zone);
        if (subscriptions.isEmpty()) {
            log.debug("No active subscriptions for zone={}", zone);
            return;
        }

        log.info("Evaluating {} subscriptions for zone={} against {} prices",
                subscriptions.size(), zone, prices.size());

        for (AlertSubscription sub : subscriptions) {
            try {
                evaluateSingleSubscription(sub, prices);
            } catch (Exception ex) {
                log.error("Error evaluating subscription id={}: {}", sub.getId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Evaluates a single subscription and dispatches a notification if the threshold
     * is crossed and the cooldown period has elapsed.
     */
    private void evaluateSingleSubscription(AlertSubscription sub, List<HourlyPrice> prices) {
        List<HourlyPrice> triggeringHours = prices.stream()
                .filter(p -> crossesThreshold(p, sub))
                .toList();

        if (triggeringHours.isEmpty()) {
            log.debug("No hours crossed threshold for subscription id={}", sub.getId());
            return;
        }

        if (!isCooldownElapsed(sub)) {
            log.debug("Cooldown still active for subscription id={}, skipping notification", sub.getId());
            return;
        }

        log.info("Dispatching notification for subscription id={} — {} triggering hours",
                sub.getId(), triggeringHours.size());

        notificationService.send(sub, triggeringHours);
        sub.setLastNotifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
        subscriptionRepository.save(sub);
    }

    /**
     * Checks whether the given price crosses the subscription's threshold in the configured direction.
     */
    boolean crossesThreshold(HourlyPrice price, AlertSubscription sub) {
        int cmp = price.getPriceEurMwh().compareTo(sub.getThresholdEurMwh());
        return switch (sub.getDirection()) {
            case BELOW -> cmp < 0;
            case ABOVE -> cmp > 0;
        };
    }

    /**
     * Returns true if the subscription's cooldown has elapsed (or if it has never been notified).
     */
    boolean isCooldownElapsed(AlertSubscription sub) {
        if (sub.getLastNotifiedAt() == null) {
            return true;
        }
        OffsetDateTime cooldownExpiry = sub.getLastNotifiedAt().plusHours(COOLDOWN_HOURS);
        return OffsetDateTime.now(ZoneOffset.UTC).isAfter(cooldownExpiry);
    }
}
