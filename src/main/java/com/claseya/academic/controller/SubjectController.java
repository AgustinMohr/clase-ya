package com.claseya.academic.controller;

import com.claseya.academic.dto.CreateSubjectRequest;
import com.claseya.academic.dto.SubjectResponse;
import com.claseya.academic.dto.UpdateSubjectRequest;
import com.claseya.academic.service.SubjectService;
import com.claseya.common.dto.SearchResultPage;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private static final CacheControl CATALOG_CACHE =
            CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    /**
     * REVIEW2-001: paginated catalog. {@code size} is capped at 100 so no client can pull the whole
     * table; the response is cacheable for a minute (the catalog changes rarely).
     */
    @GetMapping
    public ResponseEntity<SearchResultPage<SubjectResponse>> list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok()
                .cacheControl(CATALOG_CACHE)
                .body(subjectService.list(query, page, size));
    }

    /** Landing shortcut: the subjects with the most active teachers, never the full catalog. */
    @GetMapping("/popular")
    public ResponseEntity<SearchResultPage<SubjectResponse>> popular(
            @RequestParam(defaultValue = "12") int limit) {
        return ResponseEntity.ok()
                .cacheControl(CATALOG_CACHE)
                .body(subjectService.popular(limit));
    }

    @GetMapping("/{id}")
    public SubjectResponse get(@PathVariable UUID id) {
        return subjectService.get(id);
    }

    @PostMapping
    public ResponseEntity<SubjectResponse> create(@Valid @RequestBody CreateSubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectService.create(request));
    }

    @PutMapping("/{id}")
    public SubjectResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateSubjectRequest request) {
        return subjectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        subjectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
