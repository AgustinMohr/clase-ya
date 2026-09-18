package com.claseya.academic.service;

import com.claseya.academic.dto.CareerResponse;
import com.claseya.academic.dto.CreateCareerRequest;
import com.claseya.academic.dto.UpdateCareerRequest;
import com.claseya.academic.repository.AcademicUnitRepository;
import com.claseya.academic.repository.CareerRepository;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.common.util.SlugUtils;
import com.claseya.model.AcademicUnit;
import com.claseya.model.Career;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CareerService {

    private final CareerRepository careerRepository;
    private final AcademicUnitRepository academicUnitRepository;

    public CareerService(CareerRepository careerRepository,
                         AcademicUnitRepository academicUnitRepository) {
        this.careerRepository = careerRepository;
        this.academicUnitRepository = academicUnitRepository;
    }

    @Transactional(readOnly = true)
    public List<CareerResponse> listByAcademicUnit(UUID academicUnitId) {
        requireActiveAcademicUnit(academicUnitId);
        return careerRepository.findByAcademicUnit_IdAndActiveTrueOrderByNameAsc(academicUnitId).stream()
                .map(CareerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CareerResponse get(UUID id) {
        return CareerResponse.from(requireActive(id));
    }

    @Transactional
    public CareerResponse create(UUID academicUnitId, CreateCareerRequest request) {
        AcademicUnit academicUnit = requireActiveAcademicUnit(academicUnitId);

        Career career = new Career();
        career.setAcademicUnit(academicUnit);
        career.setName(request.name());
        career.setCode(request.code());
        career.setSlug(SlugUtils.uniqueSlug(SlugUtils.slugify(request.name()), careerRepository::existsBySlug));
        if (request.code() != null && !request.code().isBlank()
                && careerRepository.existsByAcademicUnit_IdAndCode(academicUnitId, request.code())) {
            throw new ConflictException("A career with this code already exists in the academic unit");
        }
        career.setActive(true);
        return CareerResponse.from(careerRepository.saveAndFlush(career));
    }

    @Transactional
    public CareerResponse update(UUID id, UpdateCareerRequest request) {
        Career career = requireActive(id);
        if (request.code() != null
                && careerRepository.existsByAcademicUnit_IdAndCode(career.getAcademicUnit().getId(), request.code())
                && !request.code().equals(career.getCode())) {
            throw new ConflictException("A career with this code already exists in the academic unit");
        }
        career.setName(request.name());
        career.setCode(request.code());
        // Slug stays stable.
        return CareerResponse.from(careerRepository.saveAndFlush(career));
    }

    @Transactional
    public void delete(UUID id) {
        Career career = careerRepository.findById(id)
                .orElseThrow(ResourceNotFoundException::new);
        career.setActive(false);
        careerRepository.save(career);
    }

    @Transactional(readOnly = true)
    public boolean careerBelongsToUniversity(UUID careerId, UUID universityId) {
        return careerRepository.existsByIdAndAcademicUnit_University_Id(careerId, universityId);
    }

    private Career requireActive(UUID id) {
        return careerRepository.findByIdAndActiveTrue(id)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private AcademicUnit requireActiveAcademicUnit(UUID academicUnitId) {
        return academicUnitRepository.findByIdAndActiveTrue(academicUnitId)
                .orElseThrow(ResourceNotFoundException::new);
    }
}
