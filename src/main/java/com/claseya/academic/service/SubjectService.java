package com.claseya.academic.service;

import com.claseya.academic.dto.CreateSubjectRequest;
import com.claseya.academic.dto.SubjectResponse;
import com.claseya.academic.dto.UpdateSubjectRequest;
import com.claseya.academic.repository.SubjectRepository;
import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.common.util.SlugUtils;
import com.claseya.model.Subject;
import com.claseya.common.dto.SearchResultPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class SubjectService {

    /** REVIEW2-001: the catalog is paginated; these are the only accepted bounds. */
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_POPULAR_LIMIT = 50;

    private final SubjectRepository subjectRepository;

    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    /**
     * REVIEW2-001 (RF-2/RF-3): paginated by name (then id, so the order is stable). The client asks
     * for what it can render instead of downloading the whole catalog.
     */
    @Transactional(readOnly = true)
    public SearchResultPage<SubjectResponse> list(String query, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        Page<Subject> result = StringUtils.hasText(query)
                ? subjectRepository.searchActiveByNameIgnoringAccents(
                        query.trim(), PageRequest.of(page, size))
                : subjectRepository.findByActiveTrue(pageable);
        return SearchResultPage.of(result.getContent().stream().map(SubjectResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /**
     * REVIEW2-001 (RF-1): what the landing shows. Only subjects that at least one ACTIVE teacher
     * actually teaches, ordered by usage and then by name.
     */
    @Transactional(readOnly = true)
    public SearchResultPage<SubjectResponse> popular(int limit) {
        if (limit < 1 || limit > MAX_POPULAR_LIMIT) {
            throw new BadRequestException("limit must be between 1 and " + MAX_POPULAR_LIMIT);
        }
        List<SubjectResponse> content = subjectRepository.findPopular(limit).stream()
                .map(SubjectResponse::from)
                .toList();
        return SearchResultPage.of(content, 0, limit, content.size());
    }

    @Transactional(readOnly = true)
    public SubjectResponse get(UUID id) {
        return SubjectResponse.from(requireActive(id));
    }

    @Transactional
    public SubjectResponse create(CreateSubjectRequest request) {
        String normalizedName = SlugUtils.normalizeName(request.name());
        if (subjectRepository.findByNormalizedName(normalizedName).isPresent()) {
            throw new ConflictException("A subject with this name already exists");
        }

        Subject subject = new Subject();
        subject.setName(request.name());
        subject.setNormalizedName(normalizedName);
        subject.setSlug(SlugUtils.uniqueSlug(SlugUtils.slugify(request.name()), subjectRepository::existsBySlug));
        subject.setDescription(request.description());
        subject.setActive(true);
        return SubjectResponse.from(subjectRepository.saveAndFlush(subject));
    }

    @Transactional
    public SubjectResponse update(UUID id, UpdateSubjectRequest request) {
        Subject subject = requireActive(id);
        String normalizedName = SlugUtils.normalizeName(request.name());
        subjectRepository.findByNormalizedName(normalizedName)
                .filter(other -> !other.getId().equals(subject.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("A subject with this name already exists");
                });

        subject.setName(request.name());
        subject.setNormalizedName(normalizedName);
        subject.setDescription(request.description());
        // Slug stays stable.
        return SubjectResponse.from(subjectRepository.saveAndFlush(subject));
    }

    @Transactional
    public void delete(UUID id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(ResourceNotFoundException::new);
        subject.setActive(false);
        subjectRepository.save(subject);
    }

    private Subject requireActive(UUID id) {
        return subjectRepository.findByIdAndActiveTrue(id)
                .orElseThrow(ResourceNotFoundException::new);
    }
}
