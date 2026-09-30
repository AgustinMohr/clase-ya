package com.claseya.verification.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.TeacherEducation;
import com.claseya.model.TeacherProfile;
import com.claseya.model.User;
import com.claseya.model.VerificationDocument;
import com.claseya.model.enums.DocumentType;
import com.claseya.teacher.repository.TeacherEducationRepository;
import com.claseya.teacher.service.TeacherProfileService;
import com.claseya.user.UserRepository;
import com.claseya.verification.dto.VerificationDocumentResponse;
import com.claseya.verification.repository.VerificationDecisionRepository;
import com.claseya.verification.repository.VerificationDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Upload / inspect / download / delete of private evidence. Submitting is a separate concern
 * ({@link VerificationSubmissionService}); uploading never changes verification state (RF-9 / I3).
 */
@Service
public class VerificationDocumentService {

    private static final int MAX_DOCUMENTS_PER_CREDENTIAL = 5;
    private static final long MAX_TOTAL_BYTES_PER_CREDENTIAL = 25L * 1024 * 1024; // 25 MB

    private final TeacherProfileService teacherProfileService;
    private final TeacherEducationRepository teacherEducationRepository;
    private final VerificationDocumentRepository documentRepository;
    private final VerificationDecisionRepository decisionRepository;
    private final DocumentContentValidator validator;
    private final DocumentStorage storage;
    private final UserRepository userRepository;

    public VerificationDocumentService(TeacherProfileService teacherProfileService,
                                       TeacherEducationRepository teacherEducationRepository,
                                       VerificationDocumentRepository documentRepository,
                                       VerificationDecisionRepository decisionRepository,
                                       DocumentContentValidator validator,
                                       DocumentStorage storage,
                                       UserRepository userRepository) {
        this.teacherProfileService = teacherProfileService;
        this.teacherEducationRepository = teacherEducationRepository;
        this.documentRepository = documentRepository;
        this.decisionRepository = decisionRepository;
        this.validator = validator;
        this.storage = storage;
        this.userRepository = userRepository;
    }

    @Transactional
    public VerificationDocumentResponse upload(UUID userId, UUID educationId, DocumentType type,
                                               byte[] content, String originalFilename) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        TeacherEducation education = requireOwnedCredential(profile, educationId);
        DocumentContentValidator.ValidatedDocument validated = validator.validate(content, originalFilename);

        if (documentRepository.countByTeacherEducation_Id(educationId) >= MAX_DOCUMENTS_PER_CREDENTIAL) {
            throw new BadRequestException("A credential cannot hold more than 5 documents");
        }
        long existingBytes = documentRepository.sumSizeBytesByEducation(educationId);
        if (existingBytes + validated.sizeBytes() > MAX_TOTAL_BYTES_PER_CREDENTIAL) {
            throw new BadRequestException("The documents of a credential cannot exceed 25 MB in total");
        }

        User uploader = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        VerificationDocument document = new VerificationDocument();
        document.setTeacher(profile);
        document.setTeacherEducation(education);
        document.setType(type);
        document.setOriginalFilename(validated.filename());
        document.setContentType(validated.contentType());
        document.setSizeBytes(validated.sizeBytes());
        document.setSha256(validated.sha256());
        document.setContent(content);
        document.setUploadedBy(uploader);
        return VerificationDocumentResponse.from(storage.store(document));
    }

    @Transactional(readOnly = true)
    public List<VerificationDocumentResponse> listByCredential(UUID userId, UUID educationId) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        requireOwnedCredential(profile, educationId);
        return documentRepository.findByTeacherEducation_IdOrderByUploadedAtDesc(educationId).stream()
                .map(VerificationDocumentResponse::from)
                .toList();
    }

    /** Owner-only download; a document of another teacher is a 404, never a 403 (I7). */
    @Transactional(readOnly = true)
    public DownloadedDocument contentForOwner(UUID userId, UUID documentId) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        VerificationDocument document = documentRepository.findById(documentId)
                .filter(d -> d.getTeacher().getId().equals(profile.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        return downloaded(document);
    }

    /** Admin download (the controller audits it); an admin may read any document. */
    @Transactional(readOnly = true)
    public DownloadedDocument contentForAdmin(UUID documentId) {
        VerificationDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        return downloaded(document);
    }

    @Transactional
    public void delete(UUID userId, UUID documentId) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        VerificationDocument document = documentRepository.findById(documentId)
                .filter(d -> d.getTeacher().getId().equals(profile.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (decisionRepository.existsByDocument_Id(documentId)) {
            throw new ConflictException("A document used as evidence cannot be deleted");
        }
        documentRepository.delete(document);
    }

    private TeacherEducation requireOwnedCredential(TeacherProfile profile, UUID educationId) {
        return teacherEducationRepository.findByIdAndTeacher_Id(educationId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher education not found"));
    }

    private DownloadedDocument downloaded(VerificationDocument document) {
        return new DownloadedDocument(document.getOriginalFilename(), document.getContentType(), document.getContent());
    }

    public record DownloadedDocument(String filename, String contentType, byte[] content) {
    }
}
