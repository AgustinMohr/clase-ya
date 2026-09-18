package com.claseya.teacher.specification;

import com.claseya.model.TeacherModality;
import com.claseya.model.TeacherProfile;
import com.claseya.model.TeacherSubject;
import com.claseya.model.enums.TeachingModality;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Teacher-search predicates.
 *
 * <p>Filters that would otherwise duplicate a teacher row across to-many
 * relations are written as EXISTS subqueries, so no DISTINCT is needed and
 * pagination/counting stay exact.
 */
public final class TeacherSpecifications {

    private TeacherSpecifications() {
    }

    /** Baseline: only verified, active teachers are publicly searchable. */
    public static Specification<TeacherProfile> visible() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("verificationStatus"), VerificationStatus.VERIFIED),
                cb.equal(root.get("user").get("status"), UserStatus.ACTIVE));
    }

    /**
     * Teachers who teach at least one active CareerSubject matching the given
     * subject/career/university combination (all present filters must hold for
     * the same CareerSubject - intersection semantics).
     */
    public static Specification<TeacherProfile> teachesAcademicCombination(UUID subjectId,
                                                                           UUID careerId,
                                                                           UUID universityId) {
        if (subjectId == null && careerId == null && universityId == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<TeacherSubject> subquery = query.subquery(TeacherSubject.class);
            Root<TeacherSubject> ts = subquery.from(TeacherSubject.class);
            subquery.select(ts);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(ts.get("teacher"), root));
            predicates.add(cb.isTrue(ts.get("active")));
            predicates.add(cb.isTrue(ts.get("careerSubject").get("active")));
            if (subjectId != null) {
                predicates.add(cb.equal(ts.get("careerSubject").get("subject").get("id"), subjectId));
            }
            if (careerId != null) {
                predicates.add(cb.equal(ts.get("careerSubject").get("career").get("id"), careerId));
            }
            if (universityId != null) {
                predicates.add(cb.equal(ts.get("careerSubject").get("career")
                        .get("academicUnit").get("university").get("id"), universityId));
            }
            subquery.where(cb.and(predicates.toArray(Predicate[]::new)));
            return cb.exists(subquery);
        };
    }

    /** Teachers offering the given modality. */
    public static Specification<TeacherProfile> hasModality(TeachingModality modality) {
        return (root, query, cb) -> {
            Subquery<TeacherModality> subquery = query.subquery(TeacherModality.class);
            Root<TeacherModality> tm = subquery.from(TeacherModality.class);
            subquery.select(tm);
            subquery.where(cb.and(
                    cb.equal(tm.get("teacher"), root),
                    cb.equal(tm.get("modality"), modality)));
            return cb.exists(subquery);
        };
    }

    public static Specification<TeacherProfile> minimumRating(BigDecimal minRating) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("ratingAverage"), cb.literal(minRating));
    }

    // ------------------------------------------------------------------ geo

    private static final BigDecimal KM_PER_DEGREE = new BigDecimal("111.32");

    /**
     * Teachers within {@code radiusKm} of (latitude, longitude). A coarse
     * bounding box pre-filters using the existing coordinates index, then the
     * exact radius is enforced by the squared planar approximation computed in
     * SQL (pure arithmetic, no function calls). Teachers without coordinates are
     * excluded.
     */
    public static Specification<TeacherProfile> withinRadius(double latitude, double longitude,
                                                             double radiusKm) {
        return (root, query, cb) -> {
            BigDecimal lat = BigDecimal.valueOf(latitude);
            BigDecimal lon = BigDecimal.valueOf(longitude);
            BigDecimal radius = BigDecimal.valueOf(radiusKm);
            BigDecimal cosLat = BigDecimal.valueOf(Math.cos(Math.toRadians(latitude)));

            // Bounding box (approximate, index-friendly).
            BigDecimal latDelta = radius.divide(KM_PER_DEGREE, 10, java.math.RoundingMode.HALF_UP);
            BigDecimal lonDelta = radius.divide(KM_PER_DEGREE.multiply(cosLat), 10,
                    java.math.RoundingMode.HALF_UP);
            Predicate box = cb.and(
                    cb.between(root.get("latitude"), lat.subtract(latDelta), lat.add(latDelta)),
                    cb.between(root.get("longitude"), lon.subtract(lonDelta), lon.add(lonDelta)));

            // Exact radius: squared-distance <= radius^2.
            Predicate circle = cb.lessThanOrEqualTo(
                    squaredDistance(root, cb, lat, lon, cosLat),
                    cb.literal(radius.multiply(radius)));

            return cb.and(box, circle);
        };
    }

    /**
     * Orders results by ascending distance. Only applied to the content query
     * (never to the count query), so it can be safely combined with others.
     */
    public static Specification<TeacherProfile> orderByDistance(double latitude, double longitude) {
        return (root, query, cb) -> {
            if (!Long.class.equals(query.getResultType())) {
                Expression<BigDecimal> sqDistance = squaredDistance(root, cb,
                        BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude),
                        BigDecimal.valueOf(Math.cos(Math.toRadians(latitude))));
                query.orderBy(cb.asc(sqDistance), cb.asc(root.get("id")));
            }
            return cb.conjunction();
        };
    }

    /**
     * (latDeltaKm)^2 + (lonDeltaKm)^2 with latDeltaKm = (lat - lat0) * 111.32 and
     * lonDeltaKm = (lon - lon0) * 111.32 * cos(lat0). Monotonic with the real
     * distance for the region scale (Santa Fe); avoids per-row trig calls.
     */
    private static Expression<BigDecimal> squaredDistance(Root<TeacherProfile> root,
                                                          jakarta.persistence.criteria.CriteriaBuilder cb,
                                                          BigDecimal lat0, BigDecimal lon0,
                                                          BigDecimal cosLat0) {
        // lat - lat0 == lat + (-lat0). Summing the negated literal avoids the
        // renderer emitting "latitude--31.6333" (two minus signs = SQL comment).
        Expression<BigDecimal> dLat = cb.sum(
                root.<BigDecimal>get("latitude"), cb.literal(lat0.negate()));
        Expression<BigDecimal> dLon = cb.sum(
                root.<BigDecimal>get("longitude"), cb.literal(lon0.negate()));
        Expression<BigDecimal> latKm = cb.prod(dLat, KM_PER_DEGREE);
        Expression<BigDecimal> lonKm = cb.prod(dLon, KM_PER_DEGREE.multiply(cosLat0));
        Expression<BigDecimal> latSq = cb.prod(latKm, latKm);
        Expression<BigDecimal> lonSq = cb.prod(lonKm, lonKm);
        return cb.sum(latSq, lonSq);
    }
}
