package com.elering.pricewatch.service;

import com.elering.pricewatch.domain.entity.AlertSubscription;
import com.elering.pricewatch.dto.request.AlertSubscriptionRequest;
import com.elering.pricewatch.dto.response.AlertSubscriptionDto;
import com.elering.pricewatch.exception.PriceNotFoundException;
import com.elering.pricewatch.mapper.AlertSubscriptionMapper;
import com.elering.pricewatch.repository.AlertSubscriptionRepository;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Manages alert subscription lifecycle (create, read, deactivate).
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertSubscriptionService {

    private final AlertSubscriptionRepository subscriptionRepository;
    private final AlertSubscriptionMapper mapper;

    /**
     * Creates a new active alert subscription after validating that the required
     * notification channel field is present.
     *
     * @param request the subscription configuration
     * @return the created subscription as a DTO
     * @throws ValidationException if the channel-specific field is missing
     */
    @Transactional
    public AlertSubscriptionDto create(AlertSubscriptionRequest request) {
        validateChannelFields(request);

        AlertSubscription entity = mapper.toEntity(request);
        entity = subscriptionRepository.save(entity);

        log.info("Created alert subscription id={} zone={} threshold={} direction={} channel={}",
                entity.getId(), entity.getZone(), entity.getThresholdEurMwh(),
                entity.getDirection(), entity.getChannel());

        return mapper.toDto(entity);
    }

    /**
     * Returns a single subscription by ID.
     *
     * @param id the subscription UUID
     * @return the subscription DTO
     * @throws PriceNotFoundException if the subscription doesn't exist
     */
    public AlertSubscriptionDto getById(UUID id) {
        return subscriptionRepository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new PriceNotFoundException("Alert subscription not found: " + id));
    }

    /**
     * Returns a paginated list of all subscriptions (active and inactive).
     *
     * @param pageable pagination and sorting parameters
     * @return page of subscription DTOs
     */
    public Page<AlertSubscriptionDto> listAll(Pageable pageable) {
        return subscriptionRepository.findAll(pageable).map(mapper::toDto);
    }

    /**
     * Soft-deactivates a subscription by setting {@code active = false}.
     * Idempotent — calling this on an already-inactive subscription is a no-op.
     *
     * @param id the subscription UUID
     * @throws PriceNotFoundException if the subscription doesn't exist
     */
    @Transactional
    public void deactivate(UUID id) {
        if (!subscriptionRepository.existsById(id)) {
            throw new PriceNotFoundException("Alert subscription not found: " + id);
        }
        int updated = subscriptionRepository.deactivateById(id);
        log.info("Deactivated alert subscription id={} (rows updated={})", id, updated);
    }

    /**
     * Validates that the required field for the chosen notification channel is present.
     */
    private void validateChannelFields(AlertSubscriptionRequest request) {
        switch (request.getChannel()) {
            case EMAIL -> {
                if (request.getEmail() == null || request.getEmail().isBlank()) {
                    throw new ValidationException("email is required when channel=EMAIL");
                }
            }
            case WEBHOOK -> {
                if (request.getWebhookUrl() == null || request.getWebhookUrl().isBlank()) {
                    throw new ValidationException("webhookUrl is required when channel=WEBHOOK");
                }
            }
            case TELEGRAM -> {
                if (request.getTelegramChatId() == null || request.getTelegramChatId().isBlank()) {
                    throw new ValidationException("telegramChatId is required when channel=TELEGRAM");
                }
            }
        }
    }
}
