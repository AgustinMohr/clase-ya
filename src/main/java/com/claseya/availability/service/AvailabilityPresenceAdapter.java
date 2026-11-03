package com.claseya.availability.service;

import com.claseya.availability.repository.AvailabilityWindowRepository;
import com.claseya.teacher.port.AvailabilityPresencePort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Adapter for the teacher-owned {@link AvailabilityPresencePort}. */
@Component
public class AvailabilityPresenceAdapter implements AvailabilityPresencePort {

    private final AvailabilityWindowRepository windowRepository;

    public AvailabilityPresenceAdapter(AvailabilityWindowRepository windowRepository) {
        this.windowRepository = windowRepository;
    }

    @Override
    public boolean existsByTeacher(UUID teacherId) {
        return windowRepository.existsByTeacher_Id(teacherId);
    }
}
