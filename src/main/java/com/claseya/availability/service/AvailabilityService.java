package com.claseya.availability.service;

import com.claseya.availability.dto.AvailabilityWindowResponse;
import com.claseya.availability.dto.CreateAvailabilityRequest;
import com.claseya.availability.dto.UpdateAvailabilityRequest;
import com.claseya.availability.repository.AvailabilityWindowRepository;
import com.claseya.availability.specification.AvailabilityWindowSpecifications;
import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.AvailabilityWindow;
import com.claseya.model.TeacherProfile;
import com.claseya.model.enums.AvailabilityDayPart;
import com.claseya.model.enums.AvailabilityStatus;
import com.claseya.model.enums.TeachingModality;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.dto.SearchResultPage;
import com.claseya.teacher.repository.TeacherProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
public class AvailabilityService {

    private static final int MIN_DURATION_MINUTES = 60;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final AvailabilityWindowRepository windowRepository;
    private final TeacherProfileRepository teacherProfileRepository;

    public AvailabilityService(AvailabilityWindowRepository windowRepository,
                               TeacherProfileRepository teacherProfileRepository) {
        this.windowRepository = windowRepository;
        this.teacherProfileRepository = teacherProfileRepository;
    }

    @Transactional
    public AvailabilityWindowResponse create(UUID userId, CreateAvailabilityRequest request) {
        TeacherProfile teacher = requireEligibleTeacher(userId);
        int start = startMinutesOf(request.startTime());
        int end = endMinutesOf(request.endTime());
        validateRange(start, end);
        ensureNoOverlap(teacher.getId(), request.dayOfWeek(), start, end, null);

        AvailabilityWindow window = new AvailabilityWindow();
        apply(window, teacher, request.dayOfWeek(), start, end, request.mode());
        try {
            return AvailabilityWindowResponse.from(windowRepository.saveAndFlush(window));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Availability window overlaps another window");
        }
    }

    @Transactional(readOnly = true)
    public SearchResultPage<AvailabilityWindowResponse> listMine(UUID userId, int page, int size) {
        validatePagination(page, size);
        TeacherProfile teacher = requireProfile(userId);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("dayOfWeek"), Sort.Order.asc("startMinutes")));
        Page<AvailabilityWindow> windows = windowRepository.findByTeacher_Id(teacher.getId(), pageable);
        return pageOf(windows);
    }

    @Transactional
    public AvailabilityWindowResponse update(UUID userId, UUID windowId,
                                             UpdateAvailabilityRequest request) {
        AvailabilityWindow window = requireOwnedWindow(userId, windowId);
        int start = startMinutesOf(request.startTime());
        int end = endMinutesOf(request.endTime());
        validateRange(start, end);
        ensureNoOverlap(window.getTeacher().getId(), request.dayOfWeek(), start, end, window.getId());

        apply(window, window.getTeacher(), request.dayOfWeek(), start, end, request.mode());
        try {
            return AvailabilityWindowResponse.from(windowRepository.saveAndFlush(window));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Availability window overlaps another window");
        }
    }

    @Transactional
    public AvailabilityWindowResponse setStatus(UUID userId, UUID windowId, AvailabilityStatus status) {
        AvailabilityWindow window = requireOwnedWindow(userId, windowId);
        window.setStatus(status);
        return AvailabilityWindowResponse.from(windowRepository.saveAndFlush(window));
    }

    @Transactional(readOnly = true)
    public SearchResultPage<AvailabilityWindowResponse> searchPublic(UUID teacherId, Integer dayOfWeek,
                                                                     TeachingModality mode,
                                                                     AvailabilityDayPart dayPart,
                                                                     int page, int size) {
        validatePagination(page, size);
        Specification<AvailabilityWindow> spec = AvailabilityWindowSpecifications.visible()
                .and(AvailabilityWindowSpecifications.publicSearch(teacherId, dayOfWeek, mode, dayPart));
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("dayOfWeek"), Sort.Order.asc("startMinutes")));
        return pageOf(windowRepository.findAll(spec, pageable));
    }

    // ------------------------------------------------------------------ helpers

    private TeacherProfile requireEligibleTeacher(UUID userId) {
        TeacherProfile teacher = requireProfile(userId);
        if (teacher.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new AccessDeniedException("Teacher must be VERIFIED to publish availability");
        }
        return teacher;
    }

    private TeacherProfile requireProfile(UUID userId) {
        return teacherProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));
    }

    private AvailabilityWindow requireOwnedWindow(UUID userId, UUID windowId) {
        return windowRepository.findByIdAndTeacher_User_Id(windowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Availability window not found"));
    }

    private void ensureNoOverlap(UUID teacherId, int day, int start, int end, UUID excludeId) {
        boolean overlaps = excludeId == null
                ? windowRepository.overlaps(teacherId, day, start, end)
                : windowRepository.overlapsExcluding(teacherId, day, start, end, excludeId);
        if (overlaps) {
            throw new ConflictException("Availability window overlaps another window of this teacher");
        }
    }

    private void validateRange(int start, int end) {
        if (end <= start) {
            throw new BadRequestException("endTime must be after startTime");
        }
        if (end - start < MIN_DURATION_MINUTES) {
            throw new BadRequestException("Availability window must last at least 1 hour");
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
    }

    private void apply(AvailabilityWindow window, TeacherProfile teacher, int day,
                       int start, int end, TeachingModality mode) {
        window.setTeacher(teacher);
        window.setDayOfWeek(day);
        window.setStartMinutes(start);
        window.setEndMinutes(end);
        window.setMode(mode);
    }

    private SearchResultPage<AvailabilityWindowResponse> pageOf(Page<AvailabilityWindow> page) {
        List<AvailabilityWindowResponse> content = page.getContent().stream()
                .map(AvailabilityWindowResponse::from)
                .toList();
        return SearchResultPage.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    // ------------------------------------------------------------------ time helpers (exposed for unit tests)

    /** Wall-clock "HH:mm" -> minutes of day (0..1439). */
    public static int startMinutesOf(String hhmm) {
        return toMinutes(hhmm);
    }

    /** Wall-clock "HH:mm" -> minutes of day; "00:00" as end means end of day (1440). */
    public static int endMinutesOf(String hhmm) {
        LocalTime time = LocalTime.parse(hhmm);
        return time == LocalTime.MIDNIGHT ? 24 * 60 : toMinutes(hhmm);
    }

    private static int toMinutes(String hhmm) {
        LocalTime time = LocalTime.parse(hhmm);
        return time.getHour() * 60 + time.getMinute();
    }

    public static int defaultSize() {
        return DEFAULT_SIZE;
    }
}
