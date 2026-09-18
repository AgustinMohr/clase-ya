package com.claseya.favorite.dto;

import com.claseya.model.Favorite;
import com.claseya.teacher.dto.TeacherSummaryResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * One saved teacher inside the student's favorites list. Embeds the public
 * teacher card so the client does not need an extra request per teacher.
 */
public record FavoriteTeacherResponse(
        UUID favoriteId,
        Instant createdAt,
        TeacherSummaryResponse teacher
) {

    public static FavoriteTeacherResponse of(Favorite favorite, TeacherSummaryResponse teacher) {
        return new FavoriteTeacherResponse(favorite.getId(), favorite.getCreatedAt(), teacher);
    }
}
