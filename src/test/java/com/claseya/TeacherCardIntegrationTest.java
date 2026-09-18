package com.claseya;

import com.claseya.model.TeacherProfile;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TeacherCardIntegrationTest extends AbstractWebIntegrationTest {

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Test
    void cardFields_areStoredAndExposedPublicly() throws Exception {
        User user = createUser("prof.card@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        TeacherProfile profile = new TeacherProfile();
        profile.setUser(user);
        profile.setVerificationStatus(VerificationStatus.VERIFIED);
        profile.setCity("Santa Fe");
        profile.setPricePerHour(new java.math.BigDecimal("15000.00"));
        profile.setPhotoUrl("https://example.com/photo.jpg");
        profile.setAvailabilityNote("Disponible por la tarde.");
        teacherProfileRepository.saveAndFlush(profile);
        userRepository.findById(user.getId()).orElseThrow().setName("Ana Card");
        userRepository.saveAndFlush(userRepository.findById(user.getId()).orElseThrow());

        String body = getJson("/api/teachers/" + profile.getId(), null, 200);
        JsonNode detail = toJson(body);
        assertThat(detail.get("city").asText()).isEqualTo("Santa Fe");
        assertThat(detail.get("pricePerHour").asDouble()).isEqualTo(15000.0);
        assertThat(detail.get("photoUrl").asText()).isEqualTo("https://example.com/photo.jpg");
        assertThat(detail.get("availabilityNote").asText()).isEqualTo("Disponible por la tarde.");
        // Private data must stay hidden.
        assertThat(body).doesNotContain("prof.card@example.com").doesNotContain("passwordHash");

        String list = getJson("/api/teachers?page=0&size=20", null, 200);
        assertThat(list).contains("\"city\":\"Santa Fe\"").contains("\"photoUrl\"");
    }
}
