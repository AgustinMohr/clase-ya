package com.claseya.security.ratelimit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Periodically drops idle buckets so a long-running process never grows without bound. */
@Component
public class RateLimitCleanupScheduler {

    private final InMemoryRateLimitStore store;
    private final RateLimitProperties properties;
    private ScheduledExecutorService executor;

    public RateLimitCleanupScheduler(InMemoryRateLimitStore store, RateLimitProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @PostConstruct
    void start() {
        long intervalMillis = Math.max(1_000, properties.getCleanupInterval().toMillis());
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rate-limit-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(store::cleanupExpired, intervalMillis, intervalMillis,
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
