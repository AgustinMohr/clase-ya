package com.claseya.student.service;

import com.claseya.academic.repository.CareerRepository;
import com.claseya.academic.repository.UniversityRepository;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.InvalidAssociationException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.Career;
import com.claseya.model.StudentProfile;
import com.claseya.model.University;
import com.claseya.student.dto.CreateStudentProfileRequest;
import com.claseya.student.dto.StudentProfileResponse;
import com.claseya.student.dto.UpdateStudentProfileRequest;
import com.claseya.student.repository.StudentProfileRepository;
import com.claseya.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StudentProfileService {

    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final CareerRepository careerRepository;

    public StudentProfileService(StudentProfileRepository studentProfileRepository,
                                 UserRepository userRepository,
                                 UniversityRepository universityRepository,
                                 CareerRepository careerRepository) {
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.universityRepository = universityRepository;
        this.careerRepository = careerRepository;
    }

    @Transactional
    public StudentProfileResponse create(UUID userId, CreateStudentProfileRequest request) {
        if (studentProfileRepository.existsByUser_Id(userId)) {
            throw new ConflictException("Student profile already exists");
        }
        University university = requireActiveUniversity(request.universityId());
        Career career = requireActiveCareerInUniversity(request.careerId(), request.universityId());

        StudentProfile profile = new StudentProfile();
        profile.setUser(userRepository.getReferenceById(userId));
        profile.setUniversity(university);
        profile.setCareer(career);
        profile.setCurrentYear(request.currentYear());
        profile.setBio(request.bio());
        return StudentProfileResponse.from(studentProfileRepository.saveAndFlush(profile));
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getByUser(UUID userId) {
        return StudentProfileResponse.from(requireByUser(userId));
    }

    @Transactional
    public StudentProfileResponse update(UUID userId, UpdateStudentProfileRequest request) {
        StudentProfile profile = requireByUser(userId);
        University university = requireActiveUniversity(request.universityId());
        Career career = requireActiveCareerInUniversity(request.careerId(), request.universityId());

        profile.setUniversity(university);
        profile.setCareer(career);
        profile.setCurrentYear(request.currentYear());
        profile.setBio(request.bio());
        return StudentProfileResponse.from(studentProfileRepository.saveAndFlush(profile));
    }

    private StudentProfile requireByUser(UUID userId) {
        return studentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
    }

    private University requireActiveUniversity(UUID universityId) {
        return universityRepository.findByIdAndActiveTrue(universityId)
                .orElseThrow(() -> new ResourceNotFoundException("University not found"));
    }

    private Career requireActiveCareerInUniversity(UUID careerId, UUID universityId) {
        Career career = careerRepository.findByIdAndActiveTrue(careerId)
                .orElseThrow(() -> new ResourceNotFoundException("Career not found"));
        if (!careerRepository.existsByIdAndAcademicUnit_University_Id(careerId, universityId)) {
            throw new InvalidAssociationException("Career does not belong to the specified university");
        }
        return career;
    }
}
