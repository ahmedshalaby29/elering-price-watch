package com.elering.pricewatch.domain.entity;

import com.elering.pricewatch.domain.enums.AlertDirection;
import com.elering.pricewatch.domain.enums.NotificationChannel;
import com.elering.pricewatch.domain.enums.Zone;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity representing a user's price-alert subscription.
 *
 * <p>A subscription defines a threshold price and a direction (BELOW/ABOVE).
 * When the fetch scheduler persists new prices, it evaluates all active subscriptions
 * and dispatches notifications through the configured channel.
 *
 * <p>At least one of {@code email}, {@code webhookUrl}, or {@code telegramChatId}
 * must be non-null, validated at the service layer based on the chosen channel.
 */
@Entity
@Table(
        name = "alert_subscriptions",
        indexes = {
                @Index(name = "idx_alert_subscriptions_zone_active",
                        columnList = "zone, active"),
                @Index(name = "idx_alert_subscriptions_active",
                        columnList = "active")
        }
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AlertSubscription {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    /** Recipient email address (required when channel = EMAIL). */
    @Email
    @Column(name = "email", length = 320)
    private String email;

    /** Webhook URL to POST to (required when channel = WEBHOOK). */
    @Column(name = "webhook_url", length = 2048)
    private String webhookUrl;

    /** Telegram chat ID to send messages to (required when channel = TELEGRAM). */
    @Column(name = "telegram_chat_id", length = 64)
    private String telegramChatId;

    /**
     * Threshold price in EUR/MWh.
     * The alert fires when the price crosses this value in the configured direction.
     */
    @Column(name = "threshold_eur_mwh", nullable = false, precision = 10, scale = 4)
    private BigDecimal thresholdEurMwh;

    /** Whether this alert fires when price is below or above the threshold. */
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 8)
    private AlertDirection direction;

    /** The Baltic zone to monitor. */
    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false, length = 4)
    private Zone zone;

    /** Whether this subscription is active; soft-delete by setting to false. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** How to deliver the notification. */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannel channel;

    /** Optional human-friendly label for the subscription. */
    @Column(name = "label", length = 255)
    private String label;

    /** UTC timestamp when this subscription was created. */
    @Column(name = "created_at", nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE", updatable = false)
    private OffsetDateTime createdAt;

    /**
     * UTC timestamp of the most recent successful notification dispatch.
     * Used to implement a 24-hour cooldown to avoid notification spam.
     */
    @Column(name = "last_notified_at",
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastNotifiedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }
}
