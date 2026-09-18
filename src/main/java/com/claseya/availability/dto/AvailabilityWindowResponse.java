package com.claseya.availability.dto;

import com.claseya.model.AvailabilityWindow;
import com.claseya.model.enums.AvailabilityDayPart;
import com.claseya.model.enums.AvailabilityStatus;
import com.claseya.model.enums.TeachingModality;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Public/owner window DTO. Times are wall-clock "HH:mm" derived from minutes of
 * day (the stored pattern). No private teacher data.
 */
public record AvailabilityWindowResponse(
        UUID id,
        UUID teacherId,
        Integer dayOfWeek,
        String startTime,
        String endTime,
        TeachingModality mode,
        AvailabilityStatus status,
        AvailabilityDayPart dayPart,
        Instant createdAt,
        Instant updatedAt
) {

    public static AvailabilityWindowResponse from(AvailabilityWindow window) {
        return new AvailabilityWindowResponse(
                window.getId(),
                window.getTeacher().getId(),
                window.getDayOfWeek(),
                minutesToTime(window.getStartMinutes()),
                minutesToTime(window.getEndMinutes()),
                window.getMode(),
                window.getStatus(),
                AvailabilityDayPart.fromStartMinutes(window.getStartMinutes()),
                window.getCreatedAt(),
                window.getUpdatedAt());
    }

    private static String minutesToTime(int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60).toString();
    }
}
