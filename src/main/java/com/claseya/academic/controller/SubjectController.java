package com.claseya.academic.controller;

import com.claseya.academic.dto.CreateSubjectRequest;
import com.claseya.academic.dto.SubjectResponse;
import com.claseya.academic.dto.UpdateSubjectRequest;
import com.claseya.academic.service.SubjectService;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public List<SubjectResponse> list(@RequestParam(required = false) String query) {
        return subjectService.list(query);
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
