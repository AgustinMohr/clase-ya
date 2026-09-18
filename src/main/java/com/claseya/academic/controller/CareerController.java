package com.claseya.academic.controller;

import com.claseya.academic.dto.CareerResponse;
import com.claseya.academic.dto.CreateCareerRequest;
import com.claseya.academic.dto.UpdateCareerRequest;
import com.claseya.academic.service.CareerService;
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
public class CareerController {

    private final CareerService careerService;

    public CareerController(CareerService careerService) {
        this.careerService = careerService;
    }

    @GetMapping("/api/academic-units/{academicUnitId}/careers")
    public List<CareerResponse> listByAcademicUnit(@PathVariable UUID academicUnitId) {
        return careerService.listByAcademicUnit(academicUnitId);
    }

    @GetMapping("/api/careers/{id}")
    public CareerResponse get(@PathVariable UUID id) {
        return careerService.get(id);
    }

    @PostMapping("/api/academic-units/{academicUnitId}/careers")
    public ResponseEntity<CareerResponse> create(@PathVariable UUID academicUnitId,
                                                 @Valid @RequestBody CreateCareerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(careerService.create(academicUnitId, request));
    }

    @PutMapping("/api/careers/{id}")
    public CareerResponse update(@PathVariable UUID id,
                                 @Valid @RequestBody UpdateCareerRequest request) {
        return careerService.update(id, request);
    }

    @DeleteMapping("/api/careers/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        careerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
