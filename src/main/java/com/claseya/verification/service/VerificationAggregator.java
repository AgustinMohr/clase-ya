package com.claseya.verification.service;

import com.claseya.model.enums.VerificationStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * RF-14 — the single source of truth for the aggregate (profile) status derived from the status of
 * the teacher's credentials. Precedence, top to bottom:
 * VERIFIED &gt; UNDER_REVIEW &gt; MORE_INFO_REQUIRED &gt; REJECTED &gt; PENDING.
 *
 * <p>PENDING credentials (created but never submitted) never lift the profile above PENDING, which
 * is exactly what the fallback row of the table requires.
 */
@Component
public class VerificationAggregator {

    public VerificationStatus aggregate(Collection<VerificationStatus> credentialStatuses) {
        if (credentialStatuses == null || credentialStatuses.isEmpty()) {
            return VerificationStatus.PENDING;
        }
        if (credentialStatuses.contains(VerificationStatus.VERIFIED)) {
            return VerificationStatus.VERIFIED;
        }
        if (credentialStatuses.contains(VerificationStatus.UNDER_REVIEW)) {
            return VerificationStatus.UNDER_REVIEW;
        }
        if (credentialStatuses.contains(VerificationStatus.MORE_INFO_REQUIRED)) {
            return VerificationStatus.MORE_INFO_REQUIRED;
        }
        if (credentialStatuses.contains(VerificationStatus.REJECTED)) {
            return VerificationStatus.REJECTED;
        }
        return VerificationStatus.PENDING;
    }
}
