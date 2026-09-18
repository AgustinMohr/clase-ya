package com.claseya.availability.controller;

import com.claseya.availability.dto.AvailabilityWindowResponse;
import com.claseya.availability.dto.CreateAvailabilityRequest;
import com.claseya.availability.dto.UpdateAvailabilityRequest;
import com.claseya.availability.service.AvailabilityService;
import com.claseya.model.enums.AvailabilityDayPart;
import com.claseya.model.enums.AvailabilityStatus;
import com.claseya.model.enums.TeachingModality;
import com.claseya.security.CurrentUser;
import com.claseya.teacher.dto.SearchResultPage;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final CurrentUser currentUser;

    public AvailabilityController(AvailabilityService availabilityService, CurrentUser currentUser) {
        this.availabilityService = availabilityService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<AvailabilityWindowResponse> create(
            @Valid @RequestBody CreateAvailabilityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(availabilityService.create(currentUser.id(), request));
    }

    @GetMapping("/me")
    public SearchResultPage<AvailabilityWindowResponse> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return availabilityService.listMine(currentUser.id(), page, size);
    }

    @PutMapping("/{id}")
    public AvailabilityWindowResponse update(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateAvailabilityRequest request) {
        return availabilityService.update(currentUser.id(), id, request);
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable UUID id) {
        availabilityService.setStatus(currentUser.id(), id, AvailabilityStatus.DISABLED);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enable(@PathVariable UUID id) {
        availabilityService.setStatus(currentUser.id(), id, AvailabilityStatus.AVAILABLE);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public SearchResultPage<AvailabilityWindowResponse> searchPublic(
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) Integer dayOfWeek,
            @RequestParam(required = false) TeachingModality mode,
            @RequestParam(required = false) AvailabilityDayPart dayPart,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return availabilityService.searchPublic(teacherId, dayOfWeek, mode, dayPart, page, size);
    }
}
