package com.claseya.academic.controller;

import com.claseya.academic.dto.AcademicUnitResponse;
import com.claseya.academic.dto.CreateAcademicUnitRequest;
import com.claseya.academic.dto.UpdateAcademicUnitRequest;
import com.claseya.academic.service.AcademicUnitService;
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
@RequestMapping
public class AcademicUnitController {

    private final AcademicUnitService academicUnitService;

    public AcademicUnitController(AcademicUnitService academicUnitService) {
        this.academicUnitService = academicUnitService;
    }

    @GetMapping("/api/universities/{universityId}/academic-units")
    public List<AcademicUnitResponse> listByUniversity(@PathVariable UUID universityId) {
        return academicUnitService.listByUniversity(universityId);
    }

    @GetMapping("/api/academic-units/{id}")
    public AcademicUnitResponse get(@PathVariable UUID id) {
        return academicUnitService.get(id);
    }

    @PostMapping("/api/universities/{universityId}/academic-units")
    public ResponseEntity<AcademicUnitResponse> create(@PathVariable UUID universityId,
                                                       @Valid @RequestBody CreateAcademicUnitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academicUnitService.create(universityId, request));
    }

    @PutMapping("/api/academic-units/{id}")
    public AcademicUnitResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody UpdateAcademicUnitRequest request) {
        return academicUnitService.update(id, request);
    }

    @DeleteMapping("/api/academic-units/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        academicUnitService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
