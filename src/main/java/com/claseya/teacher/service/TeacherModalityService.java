package com.claseya.teacher.service;

import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.TeacherModality;
import com.claseya.model.TeacherProfile;
import com.claseya.model.enums.TeachingModality;
import com.claseya.teacher.dto.AddTeacherModalityRequest;
import com.claseya.teacher.dto.TeacherModalityResponse;
import com.claseya.teacher.repository.TeacherModalityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TeacherModalityService {

    private final TeacherModalityRepository teacherModalityRepository;
    private final TeacherProfileService teacherProfileService;

    public TeacherModalityService(TeacherModalityRepository teacherModalityRepository,
                                  TeacherProfileService teacherProfileService) {
        this.teacherModalityRepository = teacherModalityRepository;
        this.teacherProfileService = teacherProfileService;
    }

    @Transactional(readOnly = true)
    public List<TeacherModalityResponse> listByUser(UUID userId) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        return teacherModalityRepository.findByTeacher_Id(profile.getId()).stream()
                .map(TeacherModalityResponse::from)
                .toList();
    }

    @Transactional
    public TeacherModalityResponse add(UUID userId, AddTeacherModalityRequest request) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        if (teacherModalityRepository.existsByTeacher_IdAndModality(profile.getId(), request.modality())) {
            throw new ConflictException("Teacher already offers this modality");
        }

        TeacherModality modality = new TeacherModality();
        modality.setTeacher(profile);
        modality.setModality(request.modality());
        return TeacherModalityResponse.from(teacherModalityRepository.saveAndFlush(modality));
    }

    @Transactional
    public void remove(UUID userId, TeachingModality modality) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        TeacherModality teacherModality = teacherModalityRepository
                .findByTeacher_IdAndModality(profile.getId(), modality)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher modality not found"));
        teacherModalityRepository.delete(teacherModality);
    }
}
