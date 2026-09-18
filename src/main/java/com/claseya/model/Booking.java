package com.claseya.model;

import com.claseya.model.enums.BookingMode;
import com.claseya.model.enums.BookingStatus;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "career_subject_id", nullable = false)
    private CareerSubject careerSubject;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private BookingMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "calendly_event_id", length = 255)
    private String calendlyEventId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
