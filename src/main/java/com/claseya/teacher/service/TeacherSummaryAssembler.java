package com.claseya.teacher.service;

import com.claseya.model.TeacherProfile;
import com.claseya.teacher.dto.TeacherSubjectView;
import com.claseya.teacher.dto.TeacherSummaryResponse;
import com.claseya.teacher.repository.TeacherModalityRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.teacher.repository.TeacherSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the public {@link TeacherSummaryResponse} cards for a set of teachers
 * using a constant number of batched queries (never N+1). Shared by teacher
 * search (Phase 4) and favorites (Phase 5).
 */
@Service
public class TeacherSummaryAssembler {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherSubjectRepository teacherSubjectRepository;
    private final TeacherModalityRepository teacherModalityRepository;

    public TeacherSummaryAssembler(TeacherProfileRepository teacherProfileRepository,
                                   TeacherSubjectRepository teacherSubjectRepository,
                                   TeacherModalityRepository teacherModalityRepository) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.teacherSubjectRepository = teacherSubjectRepository;
        this.teacherModalityRepository = teacherModalityRepository;
    }

    /**
     * Summarizes the given teachers preserving input order. {@code latitude} /
     * {@code longitude} are non-null only for geographic queries, which also
     * populates {@code distanceKm}.
     */
    @Transactional(readOnly = true)
    public List<TeacherSummaryResponse> summarize(Collection<TeacherProfile> teachers,
                                                  Double latitude, Double longitude) {
        List<UUID> ids = teachers.stream().map(TeacherProfile::getId).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        // One batch query per association group: user, subject graph, modalities.
        Map<UUID, TeacherProfile> byId = teacherProfileRepository.findWithUserByIds(ids).stream()
                .collect(Collectors.toMap(TeacherProfile::getId, Function.identity()));
        Map<UUID, List<TeacherSubjectView>> subjectsByTeacher = teacherSubjectRepository
                .findActiveWithDetailsByTeacherIds(ids).stream()
                .collect(Collectors.groupingBy(
                        ts -> ts.getTeacher().getId(),
                        Collectors.mapping(TeacherSubjectView::from, Collectors.toList())));
        Map<UUID, List<String>> modalitiesByTeacher = teacherModalityRepository
                .findByTeacher_IdIn(ids).stream()
                .collect(Collectors.groupingBy(tm -> tm.getTeacher().getId(),
                        Collectors.mapping(tm -> tm.getModality().name(),
                                Collectors.collectingAndThen(Collectors.toList(),
                                        list -> list.stream().sorted().toList()))));

        boolean geo = latitude != null && longitude != null;
        return teachers.stream()
                .map(TeacherProfile::getId)
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(profile -> toSummary(profile, subjectsByTeacher, modalitiesByTeacher,
                        geo, latitude, longitude))
                .toList();
    }

    private TeacherSummaryResponse toSummary(TeacherProfile profile,
                                             Map<UUID, List<TeacherSubjectView>> subjectsByTeacher,
                                             Map<UUID, List<String>> modalitiesByTeacher,
                                             boolean geo, Double latitude, Double longitude) {
        UUID id = profile.getId();
        Double distance = null;
        if (geo && profile.getLatitude() != null && profile.getLongitude() != null) {
            distance = haversineKm(latitude, longitude,
                    profile.getLatitude().doubleValue(), profile.getLongitude().doubleValue());
        }
        String name = profile.getUser().getName() != null ? profile.getUser().getName() : "";
        return new TeacherSummaryResponse(
                id,
                name,
                profile.getBio(),
                profile.getRatingAverage(),
                profile.getRatingCount(),
                profile.getVerificationStatus(),
                profile.getPricePerHour(),
                profile.getCity(),
                profile.getPhotoUrl(),
                modalitiesByTeacher.getOrDefault(id, List.of()),
                distance,
                subjectsByTeacher.getOrDefault(id, List.of()));
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
