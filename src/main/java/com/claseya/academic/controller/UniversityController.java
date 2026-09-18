package com.claseya.academic.controller;

import com.claseya.academic.dto.CreateUniversityRequest;
import com.claseya.academic.dto.UniversityResponse;
import com.claseya.academic.dto.UpdateUniversityRequest;
import com.claseya.academic.service.UniversityService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/universities")
public class UniversityController {

    private final UniversityService universityService;

    public UniversityController(UniversityService universityService) {
        this.universityService = universityService;
    }

    @GetMapping
    public List<UniversityResponse> list() {
        return universityService.list();
    }

    @GetMapping("/{id}")
    public UniversityResponse get(@PathVariable UUID id) {
        return universityService.get(id);
    }

    @PostMapping
    public ResponseEntity<UniversityResponse> create(@Valid @RequestBody CreateUniversityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(universityService.create(request));
    }

    @PutMapping("/{id}")
    public UniversityResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateUniversityRequest request) {
        return universityService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        universityService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
