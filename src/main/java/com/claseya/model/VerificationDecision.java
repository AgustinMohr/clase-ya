package com.claseya.model;

import com.claseya.model.enums.VerificationAction;
import com.claseya.model.enums.VerificationMethod;
import com.claseya.model.enums.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit of a verification decision (TEACHER-001). A decision about a credential has
 * {@code teacherEducation != null}; a profile-level transition (revocation) has {@code null}.
 */
@Entity
@Table(name = "verification_decisions")
@Getter
@Setter
@NoArgsConstructor
public class VerificationDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_education_id")
    private TeacherEducation teacherEducation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private VerificationDocument document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private VerificationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private VerificationStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private VerificationAction decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 40)
    private VerificationMethod method;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @CreationTimestamp
    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;
}
