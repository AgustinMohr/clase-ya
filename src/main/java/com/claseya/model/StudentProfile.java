package com.claseya.model;

import jakarta.persistence.Column;
import com.claseya.model.enums.StudentLevel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@NoArgsConstructor
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    /** Optional academic data (legacy/tertiary profiles). */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "university_id")
    private University university;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "career_id")
    private Career career;

    @Column(name = "current_year")
    private Integer currentYear;

    /** Primary onboarding attribute: PRIMARIA, SECUNDARIA, TERCIARIO, UNIVERSIDAD or ADULTO. */
    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 20)
    private StudentLevel level;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
