package com.claseya.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendly_integrations")
@Getter
@Setter
@NoArgsConstructor
public class CalendlyIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", unique = true, nullable = false)
    private TeacherProfile teacher;

    @Column(name = "calendly_user_id", length = 255)
    private String calendlyUserId;

    @Column(name = "access_token", columnDefinition = "text")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "text")
    private String refreshToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
