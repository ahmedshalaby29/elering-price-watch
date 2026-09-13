package com.elering.pricewatch.mapper;

import com.elering.pricewatch.domain.entity.AlertSubscription;
import com.elering.pricewatch.dto.request.AlertSubscriptionRequest;
import com.elering.pricewatch.dto.response.AlertSubscriptionDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * MapStruct mapper for {@link AlertSubscription} ↔ DTOs.
 *
 * <p>Sensitive fields (email, webhookUrl, telegramChatId) are masked in the
 * response DTO to avoid leaking full credentials in API responses.
 */
@Mapper(componentModel = "spring")
public interface AlertSubscriptionMapper {

    /**
     * Converts a request to an entity for persistence.
     * The {@code id}, {@code createdAt}, {@code lastNotifiedAt}, and {@code active}
     * fields are managed by the entity itself or the service layer.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastNotifiedAt", ignore = true)
    @Mapping(target = "active", constant = "true")
    AlertSubscription toEntity(AlertSubscriptionRequest request);

    /**
     * Converts an entity to the response DTO with masked sensitive fields.
     */
    @Mapping(target = "email", source = "email", qualifiedByName = "maskEmail")
    @Mapping(target = "webhookUrl", source = "webhookUrl", qualifiedByName = "maskUrl")
    @Mapping(target = "telegramChatId", source = "telegramChatId", qualifiedByName = "maskId")
    AlertSubscriptionDto toDto(AlertSubscription entity);

    @Named("maskEmail")
    default String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        int atIdx = email.indexOf('@');
        if (atIdx <= 1) return "***" + email.substring(atIdx);
        return email.charAt(0) + "***" + email.substring(atIdx);
    }

    @Named("maskUrl")
    default String maskUrl(String url) {
        if (url == null || url.isBlank()) return null;
        // Show just the scheme + host, hide the path
        try {
            var uri = java.net.URI.create(url);
            return uri.getScheme() + "://" + uri.getHost() + "/…";
        } catch (Exception e) {
            return "***";
        }
    }

    @Named("maskId")
    default String maskId(String id) {
        if (id == null || id.isBlank()) return null;
        if (id.length() <= 3) return "***";
        return id.substring(0, 3) + "***";
    }
}
