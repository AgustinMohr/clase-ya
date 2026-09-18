package com.claseya.favorite.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.favorite.dto.FavoriteResponse;
import com.claseya.favorite.dto.FavoriteStatusResponse;
import com.claseya.favorite.dto.FavoriteTeacherResponse;
import com.claseya.favorite.repository.FavoriteRepository;
import com.claseya.model.Favorite;
import com.claseya.model.StudentProfile;
import com.claseya.model.TeacherProfile;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.student.repository.StudentProfileRepository;
import com.claseya.teacher.dto.SearchResultPage;
import com.claseya.teacher.dto.TeacherSummaryResponse;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.teacher.service.TeacherSummaryAssembler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FavoriteService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final FavoriteRepository favoriteRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherSummaryAssembler teacherSummaryAssembler;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           StudentProfileRepository studentProfileRepository,
                           TeacherProfileRepository teacherProfileRepository,
                           TeacherSummaryAssembler teacherSummaryAssembler) {
        this.favoriteRepository = favoriteRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.teacherSummaryAssembler = teacherSummaryAssembler;
    }

    @Transactional
    public FavoriteResponse add(UUID userId, UUID teacherId) {
        StudentProfile student = requireStudentProfile(userId);
        TeacherProfile teacher = requireVisibleTeacher(teacherId);

        if (favoriteRepository.existsByStudent_IdAndTeacher_Id(student.getId(), teacherId)) {
            throw new ConflictException("Teacher already in favorites");
        }

        Favorite favorite = new Favorite();
        favorite.setStudent(student);
        favorite.setTeacher(teacher);
        Favorite saved = favoriteRepository.saveAndFlush(favorite);
        return FavoriteResponse.from(saved);
    }

    @Transactional
    public void remove(UUID userId, UUID teacherId) {
        StudentProfile student = studentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Favorite not found"));
        Favorite favorite = favoriteRepository
                .findByStudent_IdAndTeacher_Id(student.getId(), teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Favorite not found"));
        favoriteRepository.delete(favorite);
    }

    @Transactional(readOnly = true)
    public SearchResultPage<FavoriteTeacherResponse> list(UUID userId, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }

        StudentProfile student = studentProfileRepository.findByUser_Id(userId).orElse(null);
        if (student == null) {
            // No profile yet: nothing saved, so an empty page is the honest answer.
            return SearchResultPage.of(List.of(), page, size, 0);
        }

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Favorite> favorites = favoriteRepository.findByStudent_Id(student.getId(), pageable);

        List<TeacherProfile> teachers = favorites.getContent().stream()
                .map(Favorite::getTeacher)
                .toList();
        List<TeacherSummaryResponse> summaries =
                teacherSummaryAssembler.summarize(teachers, null, null);
        Map<UUID, TeacherSummaryResponse> byTeacherId = summaries.stream()
                .collect(Collectors.toMap(TeacherSummaryResponse::id, Function.identity()));

        List<FavoriteTeacherResponse> content = favorites.getContent().stream()
                .map(fav -> FavoriteTeacherResponse.of(fav,
                        byTeacherId.get(fav.getTeacher().getId())))
                .toList();
        return SearchResultPage.of(content, favorites.getNumber(), favorites.getSize(),
                favorites.getTotalElements());
    }

    @Transactional(readOnly = true)
    public FavoriteStatusResponse status(UUID userId, UUID teacherId) {
        StudentProfile student = studentProfileRepository.findByUser_Id(userId).orElse(null);
        if (student == null) {
            return new FavoriteStatusResponse(teacherId, false, null);
        }
        return favoriteRepository.findByStudent_IdAndTeacher_Id(student.getId(), teacherId)
                .map(fav -> new FavoriteStatusResponse(teacherId, true, fav.getId()))
                .orElseGet(() -> new FavoriteStatusResponse(teacherId, false, null));
    }

    private StudentProfile requireStudentProfile(UUID userId) {
        return studentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ConflictException(
                        "Student profile must be completed before adding favorites"));
    }

    /**
     * A teacher can only be favorited when publicly visible (VERIFIED + ACTIVE);
     * anything else is treated as not found to avoid leaking hidden profiles.
     */
    private TeacherProfile requireVisibleTeacher(UUID teacherId) {
        TeacherProfile teacher = teacherProfileRepository.findById(teacherId)
                .orElseThrow(ResourceNotFoundException::new);
        if (teacher.getVerificationStatus() != VerificationStatus.VERIFIED
                || teacher.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new ResourceNotFoundException();
        }
        return teacher;
    }

    public static int defaultSize() {
        return DEFAULT_SIZE;
    }
}
