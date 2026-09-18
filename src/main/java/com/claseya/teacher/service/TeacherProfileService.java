package com.claseya.teacher.service;

import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.TeacherModality;
import com.claseya.model.TeacherProfile;
import com.claseya.model.User;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.dto.CreateTeacherProfileRequest;
import com.claseya.teacher.dto.TeacherProfileResponse;
import com.claseya.teacher.dto.UpdateTeacherProfileRequest;
import com.claseya.teacher.repository.TeacherModalityRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TeacherProfileService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherModalityRepository teacherModalityRepository;
    private final UserRepository userRepository;

    public TeacherProfileService(TeacherProfileRepository teacherProfileRepository,
                                 TeacherModalityRepository teacherModalityRepository,
                                 UserRepository userRepository) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.teacherModalityRepository = teacherModalityRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TeacherProfileResponse create(UUID userId, CreateTeacherProfileRequest request) {
        if (teacherProfileRepository.existsByUser_Id(userId)) {
            throw new ConflictException("Teacher profile already exists");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (request.name() != null) {
            user.setName(request.name());
        }

        TeacherProfile profile = new TeacherProfile();
        profile.setUser(user);
        // System-managed fields: teachers start PENDING and cannot self-verify.
        profile.setVerificationStatus(VerificationStatus.PENDING);
        profile.setRatingAverage(BigDecimal.ZERO);
        profile.setRatingCount(0);
        applyEditableFields(profile, request.bio(), request.address(), request.availabilityNote(),
                request.pricePerHour(), request.city(), request.photoUrl(),
                request.latitude(), request.longitude());
        TeacherProfile saved = teacherProfileRepository.saveAndFlush(profile);
        return TeacherProfileResponse.from(saved, List.of());
    }

    @Transactional(readOnly = true)
    public TeacherProfileResponse getByUser(UUID userId) {
        TeacherProfile profile = requireByUser(userId);
        return TeacherProfileResponse.from(profile, modalitiesOf(profile));
    }

    @Transactional
    public TeacherProfileResponse update(UUID userId, UpdateTeacherProfileRequest request) {
        TeacherProfile profile = requireByUser(userId);
        if (request.name() != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            user.setName(request.name());
        }
        applyEditableFields(profile, request.bio(), request.address(), request.availabilityNote(),
                request.pricePerHour(), request.city(), request.photoUrl(),
                request.latitude(), request.longitude());
        TeacherProfile saved = teacherProfileRepository.saveAndFlush(profile);
        return TeacherProfileResponse.from(saved, modalitiesOf(saved));
    }

    @Transactional(readOnly = true)
    public TeacherProfile requireByUser(UUID userId) {
        return teacherProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));
    }

    private List<String> modalitiesOf(TeacherProfile profile) {
        return teacherModalityRepository.findByTeacher_Id(profile.getId()).stream()
                .map(TeacherModality::getModality)
                .map(Enum::name)
                .toList();
    }

    private void applyEditableFields(TeacherProfile profile, String bio, String address,
                                     String availabilityNote, BigDecimal pricePerHour,
                                     String city, String photoUrl,
                                     BigDecimal latitude, BigDecimal longitude) {
        profile.setBio(bio);
        profile.setAddress(address);
        if (availabilityNote != null) {
            profile.setAvailabilityNote(availabilityNote);
        }
        if (pricePerHour != null) {
            profile.setPricePerHour(pricePerHour);
        }
        if (city != null) {
            profile.setCity(city);
        }
        if (photoUrl != null) {
            profile.setPhotoUrl(photoUrl);
        }
        profile.setLatitude(latitude);
        profile.setLongitude(longitude);
    }
}
