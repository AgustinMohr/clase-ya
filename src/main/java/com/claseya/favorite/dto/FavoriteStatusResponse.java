package com.claseya.favorite.dto;

import java.util.UUID;

/**
 * Quick "is this teacher favorited?" answer for the authenticated student.
 */
public record FavoriteStatusResponse(
        UUID teacherId,
        boolean favorite,
        UUID favoriteId
) {
}
