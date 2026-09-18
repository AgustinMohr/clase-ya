package com.claseya.academic.service;

import com.claseya.academic.dto.CareerSubjectResponse;
import com.claseya.academic.dto.CreateCareerSubjectRequest;
import com.claseya.academic.dto.UpdateCareerSubjectRequest;
import com.claseya.academic.repository.CareerRepository;
import com.claseya.academic.repository.CareerSubjectRepository;
import com.claseya.academic.repository.SubjectRepository;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.Career;
import com.claseya.model.CareerSubject;
import com.claseya.model.Subject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CareerSubjectService {

    private final CareerSubjectRepository careerSubjectRepository;
    private final CareerRepository careerRepository;
    private final SubjectRepository subjectRepository;

    public CareerSubjectService(CareerSubjectRepository careerSubjectRepository,
                                CareerRepository careerRepository,
                                SubjectRepository subjectRepository) {
        this.careerSubjectRepository = careerSubjectRepository;
        this.careerRepository = careerRepository;
        this.subjectRepository = subjectRepository;
    }

    @Transactional(readOnly = true)
    public List<CareerSubjectResponse> listByCareer(UUID careerId) {
        requireActiveCareer(careerId);
        return careerSubjectRepository.findByCareer_IdAndActiveTrueOrderByYearAsc(careerId).stream()
                .map(CareerSubjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CareerSubjectResponse get(UUID id) {
        return CareerSubjectResponse.from(requireActive(id));
    }

    @Transactional
    public CareerSubjectResponse create(UUID careerId, CreateCareerSubjectRequest request) {
        Career career = requireActiveCareer(careerId);
        Subject subject = subjectRepository.findByIdAndActiveTrue(request.subjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));

        if (careerSubjectRepository.existsByCareer_IdAndSubject_Id(careerId, request.subjectId())) {
            throw new ConflictException("This subject is already part of the career");
        }

        CareerSubject careerSubject = new CareerSubject();
        careerSubject.setCareer(career);
        careerSubject.setSubject(subject);
        careerSubject.setNameOverride(request.nameOverride());
        careerSubject.setCode(request.code());
        careerSubject.setYear(request.year());
        careerSubject.setSemester(request.semester());
        careerSubject.setMandatory(request.mandatory() == null ? Boolean.TRUE : request.mandatory());
        careerSubject.setActive(true);
        return CareerSubjectResponse.from(careerSubjectRepository.saveAndFlush(careerSubject));
    }

    @Transactional
    public CareerSubjectResponse update(UUID id, UpdateCareerSubjectRequest request) {
        CareerSubject careerSubject = requireActive(id);
        careerSubject.setNameOverride(request.nameOverride());
        careerSubject.setCode(request.code());
        careerSubject.setYear(request.year());
        careerSubject.setSemester(request.semester());
        if (request.mandatory() != null) {
            careerSubject.setMandatory(request.mandatory());
        }
        return CareerSubjectResponse.from(careerSubjectRepository.saveAndFlush(careerSubject));
    }

    @Transactional
    public void delete(UUID id) {
        CareerSubject careerSubject = careerSubjectRepository.findById(id)
                .orElseThrow(ResourceNotFoundException::new);
        careerSubject.setActive(false);
        careerSubjectRepository.save(careerSubject);
    }

    private CareerSubject requireActive(UUID id) {
        return careerSubjectRepository.findByIdAndActiveTrue(id)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private Career requireActiveCareer(UUID careerId) {
        return careerRepository.findByIdAndActiveTrue(careerId)
                .orElseThrow(ResourceNotFoundException::new);
    }
}
