package com.claseya.student.controller;

import com.claseya.security.CurrentUser;
import com.claseya.student.dto.CreateStudentProfileRequest;
import com.claseya.student.dto.StudentProfileResponse;
import com.claseya.student.dto.UpdateStudentProfileRequest;
import com.claseya.student.service.StudentProfileService;
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
@RequestMapping("/api/students")
public class StudentProfileController {

    private final StudentProfileService studentProfileService;
    private final CurrentUser currentUser;

    public StudentProfileController(StudentProfileService studentProfileService, CurrentUser currentUser) {
        this.studentProfileService = studentProfileService;
        this.currentUser = currentUser;
    }

    @PostMapping("/profile")
    public ResponseEntity<StudentProfileResponse> create(@Valid @RequestBody CreateStudentProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(studentProfileService.create(currentUser.id(), request));
    }

    @GetMapping("/me")
    public StudentProfileResponse getMine() {
        return studentProfileService.getByUser(currentUser.id());
    }

    @PutMapping("/me")
    public StudentProfileResponse update(@Valid @RequestBody UpdateStudentProfileRequest request) {
        return studentProfileService.update(currentUser.id(), request);
    }
}
