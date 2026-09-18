package com.claseya.model;

import com.claseya.model.enums.AvailabilityStatus;
import com.claseya.model.enums.TeachingModality;
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

/**
 * A recurring weekly availability window published by a teacher.
 *
 * <p>Stores day of week (1 = Monday .. 7 = Sunday) and a wall-clock range in
 * minutes since midnight (hora de reloj). A recurring pattern is NOT an instant,
 * so UTC/Instant rules do not apply here; concrete dates are materialized later
 * by the booking feature. A {@code mode} of null means the teacher is available
 * in both modalities during this window.
 */
@Entity
@Table(name = "availability_windows")
@Getter
@Setter
@NoArgsConstructor
public class AvailabilityWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "start_minutes", nullable = false)
    private Integer startMinutes;

    @Column(name = "end_minutes", nullable = false)
    private Integer endMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 20)
    private TeachingModality mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AvailabilityStatus status = AvailabilityStatus.AVAILABLE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
