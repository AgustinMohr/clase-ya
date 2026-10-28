package com.claseya.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyHasherTest {

    @Test
    void hash_isStableForTheSameSaltAndValue() {
        RateLimitKeyHasher hasher = new RateLimitKeyHasher(new byte[]{1, 2, 3});

        assertThat(hasher.hash("ana@example.com"))
                .isEqualTo(hasher.hash("ana@example.com"))
                .hasSize(64);
    }

    @Test
    void hash_changesWhenTheSaltChanges() {
        RateLimitKeyHasher first = new RateLimitKeyHasher(new byte[]{1, 2, 3});
        RateLimitKeyHasher second = new RateLimitKeyHasher(new byte[]{4, 5, 6});

        assertThat(first.hash("ana@example.com")).isNotEqualTo(second.hash("ana@example.com"));
    }

    @Test
    void hash_neverContainsTheRawIdentifier() {
        RateLimitKeyHasher hasher = new RateLimitKeyHasher(new byte[]{1, 2, 3});

        assertThat(hasher.hash("ana@example.com")).doesNotContain("ana", "@", "example");
    }
}
