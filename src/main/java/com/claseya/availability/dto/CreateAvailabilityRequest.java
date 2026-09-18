package com.claseya.availability.dto;

import com.claseya.model.enums.TeachingModality;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateAvailabilityRequest(
        @Min(value = 1, message = "dayOfWeek must be between 1 (Monday) and 7 (Sunday)")
        @Max(value = 7, message = "dayOfWeek must be between 1 (Monday) and 7 (Sunday)")
        Integer dayOfWeek,

        @NotBlank(message = "startTime is required")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "startTime must be HH:mm")
        String startTime,

        @NotBlank(message = "endTime is required")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "endTime must be HH:mm")
        String endTime,

        TeachingModality mode
) {
}
