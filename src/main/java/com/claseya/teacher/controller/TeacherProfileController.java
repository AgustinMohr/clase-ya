package com.claseya.teacher.controller;

import com.claseya.security.CurrentUser;
import com.claseya.teacher.dto.CreateTeacherProfileRequest;
import com.claseya.teacher.dto.TeacherProfileResponse;
import com.claseya.teacher.dto.UpdateTeacherProfileRequest;
import com.claseya.teacher.service.TeacherProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teachers")
public class TeacherProfileController {

    private final TeacherProfileService teacherProfileService;
    private final CurrentUser currentUser;

    public TeacherProfileController(TeacherProfileService teacherProfileService, CurrentUser currentUser) {
        this.teacherProfileService = teacherProfileService;
        this.currentUser = currentUser;
    }

    @PostMapping("/profile")
    public ResponseEntity<TeacherProfileResponse> create(@Valid @RequestBody CreateTeacherProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teacherProfileService.create(currentUser.id(), request));
    }

    @GetMapping("/me")
    public TeacherProfileResponse getMine() {
        return teacherProfileService.getByUser(currentUser.id());
    }

    @PutMapping("/me")
    public TeacherProfileResponse update(@Valid @RequestBody UpdateTeacherProfileRequest request) {
        return teacherProfileService.update(currentUser.id(), request);
    }
}
