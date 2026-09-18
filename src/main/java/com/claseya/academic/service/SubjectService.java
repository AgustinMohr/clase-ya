package com.claseya.academic.service;

import com.claseya.academic.dto.CreateSubjectRequest;
import com.claseya.academic.dto.SubjectResponse;
import com.claseya.academic.dto.UpdateSubjectRequest;
import com.claseya.academic.repository.SubjectRepository;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.common.util.SlugUtils;
import com.claseya.model.Subject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;

    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> list(String query) {
        List<Subject> subjects = StringUtils.hasText(query)
                ? subjectRepository.findByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(query.trim())
                : subjectRepository.findByActiveTrueOrderByNameAsc();
        return subjects.stream().map(SubjectResponse::from).toList();
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
