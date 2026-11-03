package com.claseya.teacher.port;

import java.util.UUID;

/**
 * Port owned by {@code teacher} (the consumer) so the announcement-completeness rule does not
 * import {@code availability}'s repository. {@code availability} implements it. This keeps the
 * dependency arrow {@code availability -> teacher} and removes the package cycle.
 */
public interface AvailabilityPresencePort {

    boolean existsByTeacher(UUID teacherId);
}
