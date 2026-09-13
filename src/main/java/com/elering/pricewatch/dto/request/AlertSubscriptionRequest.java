package com.elering.pricewatch.dto.request;

import com.elering.pricewatch.domain.enums.AlertDirection;
import com.elering.pricewatch.domain.enums.NotificationChannel;
import com.elering.pricewatch.domain.enums.Zone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for creating a new alert subscription.
 */
@Data
@Schema(description = "Request to create a price-alert subscription")
public class AlertSubscriptionRequest {

    @NotNull(message = "Zone is required")
    @Schema(description = "Baltic zone to monitor", example = "EE", requiredMode = Schema.RequiredMode.REQUIRED)
    private Zone zone;

    @NotNull(message = "Threshold price is required")
    @DecimalMin(value = "-500.00", message = "Threshold must be >= -500 EUR/MWh")
    @DecimalMax(value = "5000.00", message = "Threshold must be <= 5000 EUR/MWh")
    @Schema(description = "Alert threshold in EUR/MWh", example = "50.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal thresholdEurMwh;

    @NotNull(message = "Direction is required (BELOW or ABOVE)")
    @Schema(description = "Fire alert when price is BELOW or ABOVE threshold", example = "BELOW",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private AlertDirection direction;

    @NotNull(message = "Channel is required (EMAIL, WEBHOOK, or TELEGRAM)")
    @Schema(description = "Notification delivery channel", example = "EMAIL",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private NotificationChannel channel;

    @Email(message = "Must be a valid email address")
    @Size(max = 320, message = "Email must be <= 320 characters")
    @Schema(description = "Recipient email (required when channel=EMAIL)", example = "user@example.com")
    private String email;

    @Size(max = 2048, message = "Webhook URL must be <= 2048 characters")
    @Schema(description = "Webhook URL to POST to (required when channel=WEBHOOK)",
            example = "https://hooks.slack.com/services/T000/B000/xxxx")
    private String webhookUrl;

    @Size(max = 64, message = "Telegram chat ID must be <= 64 characters")
    @Schema(description = "Telegram chat ID (required when channel=TELEGRAM)", example = "-1001234567890")
    private String telegramChatId;

    @Size(max = 255, message = "Label must be <= 255 characters")
    @Schema(description = "Optional human-readable label for this subscription",
            example = "EV charger — notify when cheap")
    private String label;
}
