package com.claseya.verification.controller;

import com.claseya.model.enums.DocumentType;
import com.claseya.security.CurrentUser;
import com.claseya.verification.dto.CredentialVerificationView;
import com.claseya.verification.dto.TeacherVerificationView;
import com.claseya.verification.dto.VerificationDocumentResponse;
import com.claseya.verification.service.TeacherVerificationService;
import com.claseya.verification.service.VerificationDocumentService;
import com.claseya.verification.service.VerificationSubmissionService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * The teacher's own verification area: status, evidence upload/download and submission. Ownership
 * always comes from {@link CurrentUser}, never from the request body.
 */
@RestController
@RequestMapping("/api/teachers/me")
public class TeacherVerificationController {

    private final TeacherVerificationService teacherVerificationService;
    private final VerificationSubmissionService submissionService;
    private final VerificationDocumentService documentService;
    private final CurrentUser currentUser;

    public TeacherVerificationController(TeacherVerificationService teacherVerificationService,
                                         VerificationSubmissionService submissionService,
                                         VerificationDocumentService documentService,
                                         CurrentUser currentUser) {
        this.teacherVerificationService = teacherVerificationService;
        this.submissionService = submissionService;
        this.documentService = documentService;
        this.currentUser = currentUser;
    }

    @GetMapping("/verification")
    public TeacherVerificationView verification() {
        return teacherVerificationService.viewForUser(currentUser.id());
    }

    /** Presents every eligible credential in one action; the profile status is recalculated. */
    @PostMapping("/verification/submit")
    public List<CredentialVerificationView> submitAll() {
        return submissionService.submitAll(currentUser.id());
    }

    @PostMapping("/education/{educationId}/documents")
    public ResponseEntity<VerificationDocumentResponse> upload(@PathVariable UUID educationId,
                                                               @RequestParam("type") DocumentType type,
                                                               @RequestParam("file") MultipartFile file)
            throws IOException {
        VerificationDocumentResponse response = documentService.upload(
                currentUser.id(), educationId, type, file.getBytes(), file.getOriginalFilename());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/education/{educationId}/documents")
    public List<VerificationDocumentResponse> listDocuments(@PathVariable UUID educationId) {
        return documentService.listByCredential(currentUser.id(), educationId);
    }

    @PostMapping("/education/{educationId}/submit")
    public CredentialVerificationView submit(@PathVariable UUID educationId) {
        return submissionService.submit(currentUser.id(), educationId);
    }

    /** Owner-only download, always as an attachment with a vetted Content-Type (never inline). */
    @GetMapping("/documents/{documentId}/content")
    public ResponseEntity<byte[]> download(@PathVariable UUID documentId) {
        VerificationDocumentService.DownloadedDocument document = documentService.contentForOwner(currentUser.id(), documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.filename()).build().toString())
                .contentType(MediaType.parseMediaType(document.contentType()))
                .body(document.content());
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID documentId) {
        documentService.delete(currentUser.id(), documentId);
        return ResponseEntity.noContent().build();
    }
}
