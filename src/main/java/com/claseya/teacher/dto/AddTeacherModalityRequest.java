package com.claseya.teacher.dto;

import com.claseya.model.enums.TeachingModality;
import jakarta.validation.constraints.NotNull;

public record AddTeacherModalityRequest(
        @NotNull(message = "modality is required")
        TeachingModality modality
) {
}
