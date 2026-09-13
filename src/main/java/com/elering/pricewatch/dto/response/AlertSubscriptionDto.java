package com.elering.pricewatch.dto.response;

import com.elering.pricewatch.domain.enums.AlertDirection;
import com.elering.pricewatch.domain.enums.NotificationChannel;
import com.elering.pricewatch.domain.enums.Zone;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for an alert subscription (sensitive fields like webhook URLs
 * are partially masked).
 */
@Value
@Builder
@Schema(description = "An alert subscription for price threshold notifications")
public class AlertSubscriptionDto {

    @Schema(description = "Subscription ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id;

    @Schema(description = "Email address (masked if present)", example = "j***@example.com")
    String email;

    @Schema(description = "Webhook URL (domain only shown)", example = "https://hooks.slack.com/…")
    String webhookUrl;

    @Schema(description = "Telegram chat ID (masked)", example = "123***")
    String telegramChatId;

    @Schema(description = "Price threshold in EUR/MWh", example = "50.00")
    BigDecimal thresholdEurMwh;

    @Schema(description = "Alert direction", example = "BELOW")
    AlertDirection direction;

    @Schema(description = "Baltic zone monitored", example = "EE")
    Zone zone;

    @Schema(description = "Whether subscription is currently active", example = "true")
    boolean active;

    @Schema(description = "Notification delivery channel", example = "EMAIL")
    NotificationChannel channel;

    @Schema(description = "Optional human-readable label", example = "EV charger alert")
    String label;

    @Schema(description = "When this subscription was created")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime createdAt;

    @Schema(description = "When the last notification was sent (null if never)")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime lastNotifiedAt;
}
