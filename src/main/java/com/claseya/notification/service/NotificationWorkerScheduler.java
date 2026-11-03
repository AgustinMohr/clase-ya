package com.claseya.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the outbox on a fixed delay (REVIEW2-002). Disabled in tests through
 * {@code app.notification.worker.enabled=false}; a multi-instance deployment is safe because the
 * claim uses {@code FOR UPDATE SKIP LOCKED}.
 */
@Component
@ConditionalOnProperty(name = "app.notification.worker.enabled",
        havingValue = "true", matchIfMissing = true)
public class NotificationWorkerScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWorkerScheduler.class);

    private final NotificationOutboxWorker worker;

    public NotificationWorkerScheduler(NotificationOutboxWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${app.notification.worker.poll-interval-ms:15000}")
    public void poll() {
        try {
            worker.processDue();
        } catch (RuntimeException ex) {
            log.error("Notification outbox poll failed; the next poll will retry", ex);
        }
    }
}
