package com.claseya.academic.service;

import com.claseya.academic.dto.CreateUniversityRequest;
import com.claseya.academic.dto.UniversityResponse;
import com.claseya.academic.dto.UpdateUniversityRequest;
import com.claseya.academic.repository.UniversityRepository;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.common.util.SlugUtils;
import com.claseya.model.University;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UniversityService {

    private final UniversityRepository universityRepository;

    public UniversityService(UniversityRepository universityRepository) {
        this.universityRepository = universityRepository;
    }

    @Transactional(readOnly = true)
    public List<UniversityResponse> list() {
        return universityRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(UniversityResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UniversityResponse get(UUID id) {
        return UniversityResponse.from(requireActive(id));
    }

    @Transactional
    public UniversityResponse create(CreateUniversityRequest request) {
        University university = new University();
        applyEditableFields(university, request.name(), request.shortName(),
                request.city(), request.province(), request.country());
        university.setSlug(SlugUtils.uniqueSlug(
                SlugUtils.slugify(request.name()), universityRepository::existsBySlug));
        university.setActive(true);
        return UniversityResponse.from(universityRepository.saveAndFlush(university));
    }

    @Transactional
    public UniversityResponse update(UUID id, UpdateUniversityRequest request) {
        University university = requireActive(id);
        applyEditableFields(university, request.name(), request.shortName(),
                request.city(), request.province(), request.country());
        // Slug stays stable to preserve any URL references to the entity.
        return UniversityResponse.from(universityRepository.saveAndFlush(university));
    }

    @Transactional
    public void delete(UUID id) {
        University university = universityRepository.findById(id)
                .orElseThrow(ResourceNotFoundException::new);
        university.setActive(false);
        universityRepository.save(university);
    }

    private University requireActive(UUID id) {
        return universityRepository.findByIdAndActiveTrue(id)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private void applyEditableFields(University university, String name, String shortName,
                                     String city, String province, String country) {
        university.setName(name);
        university.setShortName(shortName);
        university.setCity(city);
        university.setProvince(province);
        university.setCountry(country);
    }
}
