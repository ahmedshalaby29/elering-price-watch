package com.elering.pricewatch.domain.enums;

/**
 * Supported notification delivery channels for alert subscriptions.
 */
public enum NotificationChannel {

    /**
     * Send a formatted email to the subscriber's address via JavaMailSender.
     * Requires {@code MAIL_*} environment variables to be configured.
     */
    EMAIL,

    /**
     * HTTP POST a JSON payload to a user-supplied webhook URL.
     * Works with Slack incoming webhooks, n8n, Make, Zapier, etc.
     */
    WEBHOOK,

    /**
     * Send a Telegram message via the Bot API to a chat ID.
     * Requires {@code TELEGRAM_BOT_TOKEN} to be configured.
     */
    TELEGRAM
}
