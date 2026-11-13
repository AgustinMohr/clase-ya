package com.claseya.review.dto;

import com.claseya.model.Review;
import com.claseya.model.StudentProfile;
import com.claseya.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewResponseTest {

    @Test
    void publicAuthorNameExposesOnlyTheFirstName() {
        assertThat(ReviewResponse.authorName(reviewWithName("Ana María Pérez"))).isEqualTo("Ana");
    }

    @Test
    void singleWordNameIsKept() {
        assertThat(ReviewResponse.authorName(reviewWithName("Ana"))).isEqualTo("Ana");
    }

    @Test
    void blankNameFallsBackToEstudiante() {
        assertThat(ReviewResponse.authorName(reviewWithName("   "))).isEqualTo("Estudiante");
    }

    @Test
    void adminKeepsTheFullName() {
        assertThat(ReviewResponse.fullAuthorName(reviewWithName("Ana María Pérez")))
                .isEqualTo("Ana María Pérez");
    }

    private Review reviewWithName(String name) {
        User user = mock(User.class);
        when(user.getName()).thenReturn(name);
        StudentProfile student = mock(StudentProfile.class);
        when(student.getUser()).thenReturn(user);
        Review review = mock(Review.class);
        when(review.getStudent()).thenReturn(student);
        return review;
    }
}
