package com.elering.pricewatch.controller;

import com.elering.pricewatch.dto.request.AlertSubscriptionRequest;
import com.elering.pricewatch.dto.response.AlertSubscriptionDto;
import com.elering.pricewatch.service.AlertSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for managing price alert subscriptions.
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Price alert subscription management")
public class AlertController {

    private final AlertSubscriptionService alertSubscriptionService;

    @PostMapping
    @Operation(
            summary = "Create a new alert subscription",
            description = """
                    Registers a new price-alert subscription. When the scheduled price fetch detects
                    hourly prices crossing the configured threshold, a notification is dispatched
                    via the chosen channel (EMAIL / WEBHOOK / TELEGRAM).

                    Channel-specific required fields:
                    - EMAIL: `email` must be provided
                    - WEBHOOK: `webhookUrl` must be provided
                    - TELEGRAM: `telegramChatId` must be provided
                    """
    )
    public ResponseEntity<AlertSubscriptionDto> create(
            @Valid @RequestBody AlertSubscriptionRequest request) {
        AlertSubscriptionDto created = alertSubscriptionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a subscription by ID")
    public ResponseEntity<AlertSubscriptionDto> getById(
            @Parameter(description = "Subscription UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return ResponseEntity.ok(alertSubscriptionService.getById(id));
    }

    @GetMapping
    @Operation(summary = "List all subscriptions (paginated)", description = "Returns all subscriptions, active or inactive.")
    public ResponseEntity<Page<AlertSubscriptionDto>> listAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(alertSubscriptionService.listAll(pageable));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Deactivate a subscription",
            description = "Soft-deactivates the subscription — it will no longer receive notifications. " +
                          "The subscription record is retained for audit purposes."
    )
    public ResponseEntity<Void> deactivate(
            @Parameter(description = "Subscription UUID")
            @PathVariable UUID id) {
        alertSubscriptionService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
