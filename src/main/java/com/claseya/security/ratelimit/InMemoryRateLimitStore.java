package com.claseya.security.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded in-memory bucket store. Memory is capped by {@code maxEntries}; entries idle longer than
 * {@code idleTtl} are evicted periodically. The map can be read concurrently, while creation and
 * eviction are serialized to keep the hard cap. Eviction is an approximate batch (no full-map sort),
 * which is sufficient for abuse protection and keeps the hot path bounded.
 */
@Component
public class InMemoryRateLimitStore {

    private final RateLimitProperties properties;
    private final TimeMeter timeMeter;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();

    @Autowired
    public InMemoryRateLimitStore(RateLimitProperties properties) {
        this(properties, TimeMeter.SYSTEM_NANOTIME);
    }

    InMemoryRateLimitStore(RateLimitProperties properties, TimeMeter timeMeter) {
        this.properties = properties;
        this.timeMeter = timeMeter;
    }

    public Bucket bucket(String key, RateLimitPolicy policy) {
        long now = timeMeter.currentTimeNanos();
        Entry existing = entries.get(key);
        if (existing != null) {
            existing.lastAccessNanos = now;
            return existing.bucket;
        }
        synchronized (writeLock) {
            existing = entries.get(key);
            if (existing != null) {
                existing.lastAccessNanos = now;
                return existing.bucket;
            }
            evictIfNeeded();
            Bucket created = Bucket.builder()
                    .withCustomTimePrecision(timeMeter)
                    .addLimit(policy.toBandwidth())
                    .build();
            entries.put(key, new Entry(created, now));
            return created;
        }
    }

    public void reset(String key) {
        entries.remove(key);
    }

    public int size() {
        return entries.size();
    }

    /** Removes entries idle for longer than the configured TTL. Called periodically. */
    public void cleanupExpired() {
        long now = timeMeter.currentTimeNanos();
        long ttlNanos = properties.getIdleTtl().toNanos();
        entries.entrySet().removeIf(entry -> now - entry.getValue().lastAccessNanos > ttlNanos);
    }

    private void evictIfNeeded() {
        if (entries.size() < properties.getMaxEntries()) {
            return;
        }
        // Approximate batch eviction: removes at most 10% of the cap without copying or sorting
        // the map. Expired entries are handled by the periodic cleanup; strict LRU is unnecessary
        // for a defensive rate limiter. This keeps eviction O(batch) under the write lock.
        int batch = Math.max(1, properties.getMaxEntries() / 10);
        int removed = 0;
        for (String key : entries.keySet()) {
            if (removed >= batch) {
                break;
            }
            if (entries.remove(key) != null) {
                removed++;
            }
        }
    }

    private static final class Entry {

        private final Bucket bucket;
        private volatile long lastAccessNanos;

        private Entry(Bucket bucket, long lastAccessNanos) {
            this.bucket = bucket;
            this.lastAccessNanos = lastAccessNanos;
        }
    }
}
