package com.claseya.teacher.service;

import com.claseya.academic.repository.CareerSubjectRepository;
import com.claseya.academic.repository.SubjectRepository;
import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.CareerSubject;
import com.claseya.model.Subject;
import com.claseya.model.TeacherProfile;
import com.claseya.model.TeacherSubject;
import com.claseya.teacher.dto.AddTeacherSubjectRequest;
import com.claseya.teacher.dto.TeacherSubjectResponse;
import com.claseya.teacher.repository.TeacherSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TeacherSubjectService {

    private final TeacherSubjectRepository teacherSubjectRepository;
    private final CareerSubjectRepository careerSubjectRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherProfileService teacherProfileService;

    public TeacherSubjectService(TeacherSubjectRepository teacherSubjectRepository,
                                 CareerSubjectRepository careerSubjectRepository,
                                 SubjectRepository subjectRepository,
                                 TeacherProfileService teacherProfileService) {
        this.teacherSubjectRepository = teacherSubjectRepository;
        this.careerSubjectRepository = careerSubjectRepository;
        this.subjectRepository = subjectRepository;
        this.teacherProfileService = teacherProfileService;
    }

    @Transactional(readOnly = true)
    public List<TeacherSubjectResponse> listByUser(UUID userId) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        return teacherSubjectRepository.findByTeacher_Id(profile.getId()).stream()
                .map(TeacherSubjectResponse::from)
                .toList();
    }

    @Transactional
    public TeacherSubjectResponse add(UUID userId, AddTeacherSubjectRequest request) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        boolean hasCareerSubject = request.careerSubjectId() != null;
        boolean hasSubject = request.subjectId() != null;
        if (hasCareerSubject == hasSubject) {
            throw new BadRequestException(
                    "Exactly one of careerSubjectId or subjectId is required");
        }

        TeacherSubject teacherSubject = new TeacherSubject();
        teacherSubject.setTeacher(profile);
        teacherSubject.setDescription(request.description());
        teacherSubject.setYearsExperience(request.yearsExperience());
        teacherSubject.setActive(true);

        if (hasCareerSubject) {
            CareerSubject careerSubject = careerSubjectRepository
                    .findByIdAndActiveTrue(request.careerSubjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Career subject not found"));
            if (teacherSubjectRepository.existsByTeacher_IdAndCareerSubject_Id(
                    profile.getId(), request.careerSubjectId())) {
                throw new ConflictException("Teacher already teaches this subject");
            }
            teacherSubject.setCareerSubject(careerSubject);
        } else {
            Subject subject = subjectRepository.findByIdAndActiveTrue(request.subjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));
            if (teacherSubjectRepository.existsByTeacher_IdAndSubject_Id(
                    profile.getId(), request.subjectId())) {
                throw new ConflictException("Teacher already teaches this subject");
            }
            teacherSubject.setSubject(subject);
        }

        return TeacherSubjectResponse.from(teacherSubjectRepository.saveAndFlush(teacherSubject));
    }

    @Transactional
    public void remove(UUID userId, UUID teacherSubjectId) {
        TeacherSubject teacherSubject = teacherSubjectRepository
                .findByIdAndTeacher_User_Id(teacherSubjectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher subject not found"));
        teacherSubjectRepository.delete(teacherSubject);
    }
}
