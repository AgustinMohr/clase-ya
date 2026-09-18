package com.claseya.teacher.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTeacherProfileRequest(
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @Size(max = 2000, message = "Bio must be at most 2000 characters")
        String bio,

        @Size(max = 500, message = "Address must be at most 500 characters")
        String address,

        @Size(max = 2000, message = "Availability note must be at most 2000 characters")
        String availabilityNote,

        @DecimalMin(value = "0", message = "pricePerHour must be 0 or greater")
        BigDecimal pricePerHour,

        @Size(max = 100, message = "City must be at most 100 characters")
        String city,

        @Size(max = 500, message = "Photo URL must be at most 500 characters")
        String photoUrl,

        @DecimalMin(value = "-90", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90", message = "latitude must be between -90 and 90")
        BigDecimal latitude,

        @DecimalMin(value = "-180", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180", message = "longitude must be between -180 and 180")
        BigDecimal longitude
) {
}
