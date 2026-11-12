package com.claseya.notification.health;

import com.claseya.model.enums.OutboxStatus;
import com.claseya.notification.repository.NotificationOutboxRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Observability for the outbox: a DEAD row means an activation, reset or contact email could not
 * be delivered. The overall health stays HTTP 200 (details are hidden publicly) but reports
 * {@code DEGRADED}, so uptime/alerting can react without turning a single undelivered email into a
 * restart loop.
 */
@Component
public class OutboxHealthIndicator implements HealthIndicator {

    static final String DEGRADED = "DEGRADED";

    private final NotificationOutboxRepository repository;

    public OutboxHealthIndicator(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    public Health health() {
        long dead = repository.countByStatus(OutboxStatus.DEAD);
        if (dead > 0) {
            return Health.status(DEGRADED)
                    .withDetail("dead", dead)
                    .build();
        }
        return Health.up().build();
    }
}
