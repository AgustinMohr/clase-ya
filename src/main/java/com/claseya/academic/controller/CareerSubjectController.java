package com.claseya.academic.controller;

import com.claseya.academic.dto.CareerSubjectResponse;
import com.claseya.academic.dto.CreateCareerSubjectRequest;
import com.claseya.academic.dto.UpdateCareerSubjectRequest;
import com.claseya.academic.service.CareerSubjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class CareerSubjectController {

    private final CareerSubjectService careerSubjectService;

    public CareerSubjectController(CareerSubjectService careerSubjectService) {
        this.careerSubjectService = careerSubjectService;
    }

    @GetMapping("/api/careers/{careerId}/subjects")
    public List<CareerSubjectResponse> listByCareer(@PathVariable UUID careerId) {
        return careerSubjectService.listByCareer(careerId);
    }

    @GetMapping("/api/career-subjects/{id}")
    public CareerSubjectResponse get(@PathVariable UUID id) {
        return careerSubjectService.get(id);
    }

    @PostMapping("/api/careers/{careerId}/subjects")
    public ResponseEntity<CareerSubjectResponse> create(@PathVariable UUID careerId,
                                                        @Valid @RequestBody CreateCareerSubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(careerSubjectService.create(careerId, request));
    }

    @PutMapping("/api/career-subjects/{id}")
    public CareerSubjectResponse update(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateCareerSubjectRequest request) {
        return careerSubjectService.update(id, request);
    }

    @DeleteMapping("/api/career-subjects/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        careerSubjectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
