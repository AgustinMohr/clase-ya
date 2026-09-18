package com.claseya.teacher.controller;

import com.claseya.model.enums.TeachingModality;
import com.claseya.security.CurrentUser;
import com.claseya.teacher.dto.AddTeacherModalityRequest;
import com.claseya.teacher.dto.TeacherModalityResponse;
import com.claseya.teacher.service.TeacherModalityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teachers/me/modalities")
public class TeacherModalityController {

    private final TeacherModalityService teacherModalityService;
    private final CurrentUser currentUser;

    public TeacherModalityController(TeacherModalityService teacherModalityService, CurrentUser currentUser) {
        this.teacherModalityService = teacherModalityService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<TeacherModalityResponse> list() {
        return teacherModalityService.listByUser(currentUser.id());
    }

    @PostMapping
    public ResponseEntity<TeacherModalityResponse> add(@Valid @RequestBody AddTeacherModalityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teacherModalityService.add(currentUser.id(), request));
    }

    @DeleteMapping("/{modality}")
    public ResponseEntity<Void> remove(@PathVariable TeachingModality modality) {
        teacherModalityService.remove(currentUser.id(), modality);
        return ResponseEntity.noContent().build();
    }
}
