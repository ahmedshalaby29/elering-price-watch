package com.elering.pricewatch.service;

import com.elering.pricewatch.domain.entity.AlertSubscription;
import com.elering.pricewatch.domain.entity.HourlyPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Dispatches alert notifications via email, webhook, or Telegram.
 *
 * <p>Each channel can be independently enabled/disabled via config.
 * If a channel is disabled or not configured, the notification is logged
 * at INFO level but not delivered (fail-safe design).
 */
@Service
@Slf4j
public class NotificationService {

    private static final ZoneId TALLINN_TZ = ZoneId.of("Europe/Tallinn");
    private static final DateTimeFormatter HOUR_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(TALLINN_TZ);

    private final JavaMailSender mailSender;
    private final RestClient restClient;

    public NotificationService(JavaMailSender mailSender, 
                               @Qualifier("notificationRestClient") RestClient restClient) {
        this.mailSender = mailSender;
        this.restClient = restClient;
    }

    @Value("${notifications.email.enabled}")
    private boolean emailEnabled;

    @Value("${notifications.email.from}")
    private String fromAddress;

    @Value("${notifications.webhook.enabled}")
    private boolean webhookEnabled;

    @Value("${notifications.telegram.enabled}")
    private boolean telegramEnabled;

    @Value("${notifications.telegram.bot-token:}")
    private String telegramBotToken;

    @Value("${notifications.telegram.api-url}")
    private String telegramApiUrl;

    /**
     * Dispatches a notification for the given subscription and the hours that triggered it.
     *
     * @param sub              the subscription to notify
     * @param triggeringHours  hours whose price crossed the threshold
     */
    public void send(AlertSubscription sub, List<HourlyPrice> triggeringHours) {
        switch (sub.getChannel()) {
            case EMAIL -> sendEmail(sub, triggeringHours);
            case WEBHOOK -> sendWebhook(sub, triggeringHours);
            case TELEGRAM -> sendTelegram(sub, triggeringHours);
        }
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    private void sendEmail(AlertSubscription sub, List<HourlyPrice> triggeringHours) {
        if (!emailEnabled || sub.getEmail() == null || sub.getEmail().isBlank()) {
            log.info("Email notifications disabled or email missing for subscription id={}", sub.getId());
            return;
        }
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(sub.getEmail());
            helper.setSubject(buildEmailSubject(sub));
            helper.setText(buildEmailBody(sub, triggeringHours), true);
            mailSender.send(message);
            log.info("Email sent to {} for subscription id={}", sub.getEmail(), sub.getId());
        } catch (Exception ex) {
            log.error("Failed to send email for subscription id={}: {}", sub.getId(), ex.getMessage(), ex);
        }
    }

    private String buildEmailSubject(AlertSubscription sub) {
        return String.format("⚡ Elering Price Alert: Price %s %.2f EUR/MWh in %s",
                sub.getDirection() == com.elering.pricewatch.domain.enums.AlertDirection.BELOW ? "below" : "above",
                sub.getThresholdEurMwh(),
                sub.getZone().getDisplayName());
    }

    private String buildEmailBody(AlertSubscription sub, List<HourlyPrice> hours) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:Arial,sans-serif;'>");
        sb.append("<h2>⚡ Electricity Price Alert</h2>");
        sb.append(String.format("<p>Prices in <strong>%s (%s)</strong> are %s your threshold of " +
                "<strong>%.2f EUR/MWh</strong>:</p>",
                sub.getZone().getDisplayName(), sub.getZone(),
                sub.getDirection() == com.elering.pricewatch.domain.enums.AlertDirection.BELOW ? "below" : "above",
                sub.getThresholdEurMwh()));
        sb.append("<table border='1' cellpadding='8' style='border-collapse:collapse;'>");
        sb.append("<tr><th>Hour (EET/EEST)</th><th>Price (EUR/MWh)</th><th>Price incl. VAT (EUR/kWh)</th></tr>");
        for (HourlyPrice h : hours) {
            sb.append(String.format("<tr><td>%s</td><td>%.4f</td><td>%.6f</td></tr>",
                    HOUR_FMT.format(h.getHourStart()),
                    h.getPriceEurMwh(),
                    h.getPriceWithVat()));
        }
        sb.append("</table>");
        sb.append("<p style='color:#888;font-size:12px;'>Elering Price Watch | Baltic Energy Market Tracker</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    // ── Webhook ───────────────────────────────────────────────────────────────

    private void sendWebhook(AlertSubscription sub, List<HourlyPrice> triggeringHours) {
        if (!webhookEnabled || sub.getWebhookUrl() == null || sub.getWebhookUrl().isBlank()) {
            log.info("Webhook notifications disabled or URL missing for subscription id={}", sub.getId());
            return;
        }
        try {
            Map<String, Object> payload = buildWebhookPayload(sub, triggeringHours);
            restClient.post()
                    .uri(sub.getWebhookUrl())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Webhook delivered to {} for subscription id={}", sub.getWebhookUrl(), sub.getId());
        } catch (Exception ex) {
            log.error("Failed to deliver webhook for subscription id={}: {}", sub.getId(), ex.getMessage(), ex);
        }
    }

    private Map<String, Object> buildWebhookPayload(AlertSubscription sub, List<HourlyPrice> hours) {
        List<Map<String, Object>> hourList = hours.stream().map(h -> Map.<String, Object>of(
                "hourStart", h.getHourStart().toString(),
                "priceEurMwh", h.getPriceEurMwh(),
                "priceWithVat", h.getPriceWithVat()
        )).toList();

        return Map.of(
                "event", "price_alert",
                "subscriptionId", sub.getId().toString(),
                "zone", sub.getZone().toString(),
                "direction", sub.getDirection().toString(),
                "thresholdEurMwh", sub.getThresholdEurMwh(),
                "label", sub.getLabel() != null ? sub.getLabel() : "",
                "triggeringHours", hourList
        );
    }

    // ── Telegram ──────────────────────────────────────────────────────────────

    private void sendTelegram(AlertSubscription sub, List<HourlyPrice> triggeringHours) {
        if (!telegramEnabled || telegramBotToken.isBlank() || sub.getTelegramChatId() == null) {
            log.info("Telegram notifications disabled or not configured for subscription id={}", sub.getId());
            return;
        }
        try {
            String text = buildTelegramMessage(sub, triggeringHours);
            String url = telegramApiUrl + "/bot" + telegramBotToken + "/sendMessage";
            restClient.post()
                    .uri(url)
                    .body(Map.of(
                            "chat_id", sub.getTelegramChatId(),
                            "text", text,
                            "parse_mode", "HTML"
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Telegram message sent to chat_id={} for subscription id={}",
                    sub.getTelegramChatId(), sub.getId());
        } catch (Exception ex) {
            log.error("Failed to send Telegram message for subscription id={}: {}",
                    sub.getId(), ex.getMessage(), ex);
        }
    }

    private String buildTelegramMessage(AlertSubscription sub, List<HourlyPrice> hours) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚡ <b>Electricity Price Alert</b>\n\n");
        sb.append(String.format("Zone: <b>%s (%s)</b>\n", sub.getZone().getDisplayName(), sub.getZone()));
        sb.append(String.format("Prices %s threshold of <b>%.2f EUR/MWh</b>:\n\n",
                sub.getDirection() == com.elering.pricewatch.domain.enums.AlertDirection.BELOW ? "BELOW" : "ABOVE",
                sub.getThresholdEurMwh()));

        BigDecimal avgPrice = hours.stream()
                .map(HourlyPrice::getPriceEurMwh)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(hours.size()), 2, RoundingMode.HALF_UP);

        for (HourlyPrice h : hours) {
            sb.append(String.format("  %s → <code>%.2f EUR/MWh</code>\n",
                    HOUR_FMT.format(h.getHourStart()),
                    h.getPriceEurMwh()));
        }
        sb.append(String.format("\nAverage: <b>%.2f EUR/MWh</b>", avgPrice));

        if (sub.getLabel() != null && !sub.getLabel().isBlank()) {
            sb.append("\n\uD83C\uDFF7 ").append(sub.getLabel());
        }
        return sb.toString();
    }
}
