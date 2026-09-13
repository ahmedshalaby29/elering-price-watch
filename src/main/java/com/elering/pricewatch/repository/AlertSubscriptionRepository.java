package com.elering.pricewatch.repository;

import com.elering.pricewatch.domain.entity.AlertSubscription;
import com.elering.pricewatch.domain.enums.Zone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AlertSubscription} entities.
 */
@Repository
public interface AlertSubscriptionRepository extends JpaRepository<AlertSubscription, UUID> {

    /**
     * Returns all active subscriptions for a specific zone.
     * Called by the alert evaluator after each price fetch.
     *
     * @param zone the Baltic pricing zone to filter by
     * @return active subscriptions monitoring the given zone
     */
    List<AlertSubscription> findByZoneAndActiveTrue(Zone zone);

    /**
     * Returns all active subscriptions across all zones.
     * Used when we need to evaluate alerts for multiple zones in one pass.
     *
     * @return all active subscriptions
     */
    List<AlertSubscription> findByActiveTrue();

    /**
     * Paged listing of all subscriptions (active or not), for the admin list endpoint.
     *
     * @param pageable pagination parameters
     * @return page of subscriptions
     */
    Page<AlertSubscription> findAll(Pageable pageable);

    /**
     * Soft-deletes a subscription by setting {@code active = false}.
     *
     * @param id the subscription ID to deactivate
     * @return number of rows affected (0 if not found, 1 if found)
     */
    @Modifying
    @Query("UPDATE AlertSubscription a SET a.active = false WHERE a.id = :id")
    int deactivateById(@Param("id") UUID id);
}
