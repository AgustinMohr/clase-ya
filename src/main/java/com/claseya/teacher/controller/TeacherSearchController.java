package com.claseya.teacher.controller;

import com.claseya.model.enums.TeachingModality;
import com.claseya.teacher.dto.SearchResultPage;
import com.claseya.teacher.dto.TeacherPublicDetailResponse;
import com.claseya.teacher.dto.TeacherSearchCriteria;
import com.claseya.teacher.dto.TeacherSummaryResponse;
import com.claseya.teacher.service.TeacherSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public teacher discovery. Only verified + active teachers are returned;
 * visibility is enforced by the backend, never by the client.
 */
@RestController
@RequestMapping("/api/teachers")
public class TeacherSearchController {

    private final TeacherSearchService teacherSearchService;

    public TeacherSearchController(TeacherSearchService teacherSearchService) {
        this.teacherSearchService = teacherSearchService;
    }

    @GetMapping
    public SearchResultPage<TeacherSummaryResponse> search(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID careerId,
            @RequestParam(required = false) UUID universityId,
            @RequestParam(required = false) TeachingModality modality,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radius,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "rating") String sort) {

        TeacherSearchCriteria criteria = new TeacherSearchCriteria(
                subjectId, careerId, universityId, modality, minRating,
                latitude, longitude, radius, page, size, sort);
        return teacherSearchService.search(criteria);
    }

    @GetMapping("/{id}")
    public TeacherPublicDetailResponse get(@PathVariable UUID id) {
        return teacherSearchService.getPublic(id);
    }
}
