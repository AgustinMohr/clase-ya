package com.claseya.availability.specification;

import com.claseya.model.AvailabilityWindow;
import com.claseya.model.TeacherProfile;
import com.claseya.model.enums.AvailabilityDayPart;
import com.claseya.model.enums.AvailabilityStatus;
import com.claseya.model.enums.TeachingModality;
import com.claseya.teacher.specification.TeacherSpecifications;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Public availability queries. A window is publicly visible only when it is AVAILABLE and its
 * teacher satisfies the full D1 rule (ACTIVE user + published announcement), the same definition
 * used by search, the public profile and favorites. Optional filters: teacher, day, mode, day part.
 */
public final class AvailabilityWindowSpecifications {

    private AvailabilityWindowSpecifications() {
    }

    public static Specification<AvailabilityWindow> visible() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), AvailabilityStatus.AVAILABLE),
                TeacherSpecifications.visibleTeacher(root.<TeacherProfile>get("teacher"), query, cb));
    }

    public static Specification<AvailabilityWindow> publicSearch(UUID teacherId, Integer dayOfWeek,
                                                                 TeachingModality mode,
                                                                 AvailabilityDayPart dayPart) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (teacherId != null) {
                predicates.add(cb.equal(root.get("teacher").get("id"), teacherId));
            }
            if (dayOfWeek != null) {
                predicates.add(cb.equal(root.get("dayOfWeek"), dayOfWeek));
            }
            if (mode != null) {
                predicates.add(cb.equal(root.get("mode"), mode));
            }
            if (dayPart != null) {
                int from;
                int to;
                switch (dayPart) {
                    case AFTERNOON -> {
                        from = 12 * 60;
                        to = 19 * 60 - 1;
                    }
                    case NIGHT -> {
                        from = 19 * 60;
                        to = 24 * 60 - 1;
                    }
                    default -> {
                        from = 0;
                        to = 12 * 60 - 1;
                    }
                }
                predicates.add(cb.between(root.get("startMinutes"), from, to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
