package com.claseya.security.ratelimit;

import io.github.bucket4j.TimeMeter;

import java.time.Duration;

/** Deterministic clock for Bucket4j and the store; tests never sleep. */
final class MutableTimeMeter implements TimeMeter {

    private long nanos;

    MutableTimeMeter() {
        this(1_000_000_000L);
    }

    MutableTimeMeter(long startNanos) {
        this.nanos = startNanos;
    }

    @Override
    public long currentTimeNanos() {
        return nanos;
    }

    @Override
    public boolean isWallClockBased() {
        return false;
    }

    void advance(Duration duration) {
        nanos += duration.toNanos();
    }
}
