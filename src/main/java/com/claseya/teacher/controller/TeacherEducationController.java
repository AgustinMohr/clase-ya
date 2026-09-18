package com.claseya.teacher.controller;

import com.claseya.security.CurrentUser;
import com.claseya.teacher.dto.CreateTeacherEducationRequest;
import com.claseya.teacher.dto.TeacherEducationResponse;
import com.claseya.teacher.dto.UpdateTeacherEducationRequest;
import com.claseya.teacher.service.TeacherEducationService;
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
@RequestMapping("/api/teachers/me/education")
public class TeacherEducationController {

    private final TeacherEducationService teacherEducationService;
    private final CurrentUser currentUser;

    public TeacherEducationController(TeacherEducationService teacherEducationService, CurrentUser currentUser) {
        this.teacherEducationService = teacherEducationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<TeacherEducationResponse> list() {
        return teacherEducationService.listByUser(currentUser.id());
    }

    @PostMapping
    public ResponseEntity<TeacherEducationResponse> create(
            @Valid @RequestBody CreateTeacherEducationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teacherEducationService.create(currentUser.id(), request));
    }

    @PutMapping("/{id}")
    public TeacherEducationResponse update(@PathVariable UUID id,
                                           @Valid @RequestBody UpdateTeacherEducationRequest request) {
        return teacherEducationService.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        teacherEducationService.delete(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
