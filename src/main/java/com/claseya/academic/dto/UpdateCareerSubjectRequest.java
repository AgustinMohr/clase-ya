package com.claseya.academic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateCareerSubjectRequest(
        @Size(max = 255, message = "Name override must be at most 255 characters")
        String nameOverride,

        @Size(max = 50, message = "Code must be at most 50 characters")
        String code,

        @Min(value = 1, message = "Year must be at least 1")
        Integer year,

        @Min(value = 1, message = "Semester must be between 1 and 2")
        @Max(value = 2, message = "Semester must be between 1 and 2")
        Integer semester,

        Boolean mandatory
) {
}
