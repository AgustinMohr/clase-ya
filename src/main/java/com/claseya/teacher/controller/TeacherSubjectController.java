package com.claseya.teacher.controller;

import com.claseya.security.CurrentUser;
import com.claseya.teacher.dto.AddTeacherSubjectRequest;
import com.claseya.teacher.dto.TeacherSubjectResponse;
import com.claseya.teacher.service.TeacherSubjectService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/teachers/me/subjects")
public class TeacherSubjectController {

    private final TeacherSubjectService teacherSubjectService;
    private final CurrentUser currentUser;

    public TeacherSubjectController(TeacherSubjectService teacherSubjectService, CurrentUser currentUser) {
        this.teacherSubjectService = teacherSubjectService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<TeacherSubjectResponse> list() {
        return teacherSubjectService.listByUser(currentUser.id());
    }

    @PostMapping
    public ResponseEntity<TeacherSubjectResponse> add(@Valid @RequestBody AddTeacherSubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teacherSubjectService.add(currentUser.id(), request));
    }

    @DeleteMapping("/{careerSubjectId}")
    public ResponseEntity<Void> remove(@PathVariable UUID careerSubjectId) {
        teacherSubjectService.remove(currentUser.id(), careerSubjectId);
        return ResponseEntity.noContent().build();
    }
}
