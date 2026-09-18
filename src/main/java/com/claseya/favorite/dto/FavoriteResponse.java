package com.claseya.favorite.dto;

import com.claseya.model.Favorite;

import java.time.Instant;
import java.util.UUID;

public record FavoriteResponse(
        UUID id,
        UUID teacherId,
        Instant createdAt
) {

    public static FavoriteResponse from(Favorite favorite) {
        return new FavoriteResponse(
                favorite.getId(),
                favorite.getTeacher().getId(),
                favorite.getCreatedAt());
    }
}
