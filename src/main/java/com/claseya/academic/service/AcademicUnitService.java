package com.claseya.academic.service;

import com.claseya.academic.dto.AcademicUnitResponse;
import com.claseya.academic.dto.CreateAcademicUnitRequest;
import com.claseya.academic.dto.UpdateAcademicUnitRequest;
import com.claseya.academic.repository.AcademicUnitRepository;
import com.claseya.academic.repository.UniversityRepository;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.AcademicUnit;
import com.claseya.model.University;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AcademicUnitService {

    private final AcademicUnitRepository academicUnitRepository;
    private final UniversityRepository universityRepository;

    public AcademicUnitService(AcademicUnitRepository academicUnitRepository,
                               UniversityRepository universityRepository) {
        this.academicUnitRepository = academicUnitRepository;
        this.universityRepository = universityRepository;
    }

    @Transactional(readOnly = true)
    public List<AcademicUnitResponse> listByUniversity(UUID universityId) {
        requireActiveUniversity(universityId);
        return academicUnitRepository.findByUniversity_IdAndActiveTrueOrderByNameAsc(universityId).stream()
                .map(AcademicUnitResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AcademicUnitResponse get(UUID id) {
        return AcademicUnitResponse.from(requireActive(id));
    }

    @Transactional
    public AcademicUnitResponse create(UUID universityId, CreateAcademicUnitRequest request) {
        University university = requireActiveUniversity(universityId);
        ensureUnique(universityId, request.name(), request.code());

        AcademicUnit unit = new AcademicUnit();
        unit.setUniversity(university);
        unit.setName(request.name());
        unit.setCode(request.code());
        unit.setActive(true);
        return AcademicUnitResponse.from(academicUnitRepository.saveAndFlush(unit));
    }

    @Transactional
    public AcademicUnitResponse update(UUID id, UpdateAcademicUnitRequest request) {
        AcademicUnit unit = requireActive(id);
        if (academicUnitRepository.existsByUniversity_IdAndName(unit.getUniversity().getId(), request.name())
                && !unit.getName().equals(request.name())) {
            throw new ConflictException("An academic unit with this name already exists in the university");
        }
        if (request.code() != null
                && academicUnitRepository.existsByUniversity_IdAndCode(unit.getUniversity().getId(), request.code())
                && !request.code().equals(unit.getCode())) {
            throw new ConflictException("An academic unit with this code already exists in the university");
        }
        unit.setName(request.name());
        unit.setCode(request.code());
        return AcademicUnitResponse.from(academicUnitRepository.saveAndFlush(unit));
    }

    @Transactional
    public void delete(UUID id) {
        AcademicUnit unit = academicUnitRepository.findById(id)
                .orElseThrow(ResourceNotFoundException::new);
        unit.setActive(false);
        academicUnitRepository.save(unit);
    }

    private void ensureUnique(UUID universityId, String name, String code) {
        if (academicUnitRepository.existsByUniversity_IdAndName(universityId, name)) {
            throw new ConflictException("An academic unit with this name already exists in the university");
        }
        if (code != null && !code.isBlank()
                && academicUnitRepository.existsByUniversity_IdAndCode(universityId, code)) {
            throw new ConflictException("An academic unit with this code already exists in the university");
        }
    }

    private University requireActiveUniversity(UUID universityId) {
        return universityRepository.findByIdAndActiveTrue(universityId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private AcademicUnit requireActive(UUID id) {
        return academicUnitRepository.findByIdAndActiveTrue(id)
                .orElseThrow(ResourceNotFoundException::new);
    }
}
