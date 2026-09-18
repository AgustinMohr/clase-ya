package com.claseya.teacher.dto;

import com.claseya.model.TeacherModality;
import com.claseya.model.enums.TeachingModality;

import java.util.UUID;

public record TeacherModalityResponse(
        UUID id,
        TeachingModality modality
) {

    public static TeacherModalityResponse from(TeacherModality teacherModality) {
        return new TeacherModalityResponse(teacherModality.getId(), teacherModality.getModality());
    }
}
