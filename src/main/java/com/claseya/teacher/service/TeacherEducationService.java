package com.claseya.teacher.service;

import com.claseya.common.exception.InvalidAssociationException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.TeacherEducation;
import com.claseya.model.TeacherProfile;
import com.claseya.teacher.dto.CreateTeacherEducationRequest;
import com.claseya.teacher.dto.TeacherEducationResponse;
import com.claseya.teacher.dto.UpdateTeacherEducationRequest;
import com.claseya.teacher.repository.TeacherEducationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TeacherEducationService {

    private final TeacherEducationRepository teacherEducationRepository;
    private final TeacherProfileService teacherProfileService;

    public TeacherEducationService(TeacherEducationRepository teacherEducationRepository,
                                   TeacherProfileService teacherProfileService) {
        this.teacherEducationRepository = teacherEducationRepository;
        this.teacherProfileService = teacherProfileService;
    }

    @Transactional(readOnly = true)
    public List<TeacherEducationResponse> listByUser(UUID userId) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        return teacherEducationRepository.findByTeacher_IdOrderByStartYearDesc(profile.getId()).stream()
                .map(TeacherEducationResponse::from)
                .toList();
    }

    @Transactional
    public TeacherEducationResponse create(UUID userId, CreateTeacherEducationRequest request) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        validateYears(request.startYear(), request.endYear());

        TeacherEducation education = new TeacherEducation();
        education.setTeacher(profile);
        education.setInstitution(request.institution());
        education.setDegree(request.degree());
        education.setDescription(request.description());
        education.setStartYear(request.startYear());
        education.setEndYear(request.endYear());
        // isVerified is system-managed (admin), never set by the teacher.
        education.setIsVerified(false);
        return TeacherEducationResponse.from(teacherEducationRepository.saveAndFlush(education));
    }

    @Transactional
    public TeacherEducationResponse update(UUID userId, UUID educationId, UpdateTeacherEducationRequest request) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        TeacherEducation education = teacherEducationRepository
                .findByIdAndTeacher_Id(educationId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher education not found"));
        validateYears(request.startYear(), request.endYear());

        education.setInstitution(request.institution());
        education.setDegree(request.degree());
        education.setDescription(request.description());
        education.setStartYear(request.startYear());
        education.setEndYear(request.endYear());
        return TeacherEducationResponse.from(teacherEducationRepository.saveAndFlush(education));
    }

    @Transactional
    public void delete(UUID userId, UUID educationId) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        TeacherEducation education = teacherEducationRepository
                .findByIdAndTeacher_Id(educationId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher education not found"));
        teacherEducationRepository.delete(education);
    }

    private void validateYears(Integer startYear, Integer endYear) {
        if (startYear != null && endYear != null && endYear < startYear) {
            throw new InvalidAssociationException("endYear must not be before startYear");
        }
    }
}
