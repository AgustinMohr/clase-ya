package com.claseya;

import com.claseya.model.enums.VerificationStatus;
import com.claseya.verification.service.VerificationAggregator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.claseya.model.enums.VerificationStatus.MORE_INFO_REQUIRED;
import static com.claseya.model.enums.VerificationStatus.PENDING;
import static com.claseya.model.enums.VerificationStatus.REJECTED;
import static com.claseya.model.enums.VerificationStatus.UNDER_REVIEW;
import static com.claseya.model.enums.VerificationStatus.VERIFIED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-14 exhaustively: the aggregate profile status derived from credential statuses. Covers every
 * row of the table plus the explicit combinations the spec calls out.
 */
class VerificationAggregatorTest {

    private final VerificationAggregator aggregator = new VerificationAggregator();

    @Test
    void neverSubmitted_remainsPending() {
        assertThat(aggregator.aggregate(List.of())).isEqualTo(PENDING);
        assertThat(aggregator.aggregate(List.of(PENDING))).isEqualTo(PENDING);
        assertThat(aggregator.aggregate(List.of(PENDING, PENDING))).isEqualTo(PENDING);
    }

    @Test
    void singleCredential_mapsToItsOwnStatus() {
        assertThat(aggregator.aggregate(List.of(UNDER_REVIEW))).isEqualTo(UNDER_REVIEW);
        assertThat(aggregator.aggregate(List.of(MORE_INFO_REQUIRED))).isEqualTo(MORE_INFO_REQUIRED);
        assertThat(aggregator.aggregate(List.of(REJECTED))).isEqualTo(REJECTED);
        assertThat(aggregator.aggregate(List.of(VERIFIED))).isEqualTo(VERIFIED);
    }

    @Test
    void verified_winsOverEveryOtherState() {
        assertThat(aggregator.aggregate(List.of(VERIFIED, REJECTED))).isEqualTo(VERIFIED);
        assertThat(aggregator.aggregate(List.of(VERIFIED, MORE_INFO_REQUIRED))).isEqualTo(VERIFIED);
        assertThat(aggregator.aggregate(List.of(VERIFIED, UNDER_REVIEW))).isEqualTo(VERIFIED);
        assertThat(aggregator.aggregate(List.of(PENDING, VERIFIED, REJECTED))).isEqualTo(VERIFIED);
    }

    @Test
    void underReview_beatsLowerStates() {
        assertThat(aggregator.aggregate(List.of(UNDER_REVIEW, REJECTED))).isEqualTo(UNDER_REVIEW);
        assertThat(aggregator.aggregate(List.of(UNDER_REVIEW, MORE_INFO_REQUIRED))).isEqualTo(UNDER_REVIEW);
        assertThat(aggregator.aggregate(List.of(PENDING, UNDER_REVIEW))).isEqualTo(UNDER_REVIEW);
    }

    @Test
    void moreInfoRequired_beatsRejected() {
        assertThat(aggregator.aggregate(List.of(MORE_INFO_REQUIRED, REJECTED))).isEqualTo(MORE_INFO_REQUIRED);
        assertThat(aggregator.aggregate(List.of(PENDING, MORE_INFO_REQUIRED))).isEqualTo(MORE_INFO_REQUIRED);
    }

    @Test
    void rejected_onlyWhenEveryPresentedCredentialIsRejected() {
        assertThat(aggregator.aggregate(List.of(REJECTED, PENDING))).isEqualTo(REJECTED);
        assertThat(aggregator.aggregate(List.of(REJECTED, REJECTED))).isEqualTo(REJECTED);
    }

    @Test
    void revokingTheOnlyVerifiedCredential_dropsOutOfVerified() {
        // After a revoke the credential is REJECTED; if it was the only VERIFIED one the profile
        // must never stay VERIFIED and must instead follow the table.
        assertThat(aggregator.aggregate(List.of(REJECTED))).isEqualTo(REJECTED);
        assertThat(aggregator.aggregate(List.of(REJECTED, MORE_INFO_REQUIRED))).isEqualTo(MORE_INFO_REQUIRED);
    }
}
