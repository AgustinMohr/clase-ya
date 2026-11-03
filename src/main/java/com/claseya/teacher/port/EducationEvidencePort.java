package com.claseya.teacher.port;

import java.util.UUID;

/**
 * Port owned by {@code teacher} (the consumer) so credential deletion can ask whether evidence
 * exists without importing {@code verification}'s repositories. {@code verification} implements it.
 * This keeps the dependency arrow {@code verification -> teacher} and removes the package cycle.
 */
public interface EducationEvidencePort {

    boolean hasEvidence(UUID educationId);

    boolean hasDecisions(UUID educationId);
}
