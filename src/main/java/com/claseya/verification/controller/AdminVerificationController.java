package com.claseya.verification.controller;

import com.claseya.model.enums.VerificationStatus;
import com.claseya.security.CurrentUser;
import com.claseya.teacher.dto.SearchResultPage;
import com.claseya.verification.dto.AdminVerificationDetail;
import com.claseya.verification.dto.VerificationDecisionRequest;
import com.claseya.verification.dto.VerificationDecisionView;
import com.claseya.verification.dto.VerificationQueueItem;
import com.claseya.verification.dto.VerificationRevokeRequest;
import com.claseya.verification.service.VerificationDecisionService;
import com.claseya.verification.service.VerificationDocumentService;
import com.claseya.verification.service.VerificationQueryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Review side (ADMIN only, enforced again in SecurityConfig). The reviewer's identity is taken from
 * the security context, never from the request.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminVerificationController {

    private static final Logger log = LoggerFactory.getLogger(AdminVerificationController.class);

    private final VerificationQueryService queryService;
    private final VerificationDecisionService decisionService;
    private final VerificationDocumentService documentService;
    private final CurrentUser currentUser;

    public AdminVerificationController(VerificationQueryService queryService,
                                       VerificationDecisionService decisionService,
                                       VerificationDocumentService documentService,
                                       CurrentUser currentUser) {
        this.queryService = queryService;
        this.decisionService = decisionService;
        this.documentService = documentService;
        this.currentUser = currentUser;
    }

    /** Review queue; defaults to UNDER_REVIEW, oldest presentation first, paginated. */
    @GetMapping("/verifications")
    public SearchResultPage<VerificationQueueItem> queue(
            @RequestParam(required = false) VerificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.queue(status, page, size);
    }

    @GetMapping("/verifications/{teacherId}")
    public AdminVerificationDetail detail(@PathVariable UUID teacherId) {
        return queryService.adminDetail(teacherId);
    }

    /** Admin download. Audited at the application log (never the document content itself). */
    @GetMapping("/documents/{documentId}/content")
    public ResponseEntity<byte[]> documentContent(@PathVariable UUID documentId) {
        log.info("Admin {} downloaded verification document {}", currentUser.id(), documentId);
        VerificationDocumentService.DownloadedDocument document = documentService.contentForAdmin(documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.filename()).build().toString())
                .contentType(MediaType.parseMediaType(document.contentType()))
                .body(document.content());
    }

    @PostMapping("/verifications/{teacherId}/credentials/{educationId}/decision")
    public VerificationDecisionView decide(@PathVariable UUID teacherId,
                                           @PathVariable UUID educationId,
                                           @Valid @RequestBody VerificationDecisionRequest request) {
        return decisionService.decide(currentUser.id(), teacherId, educationId,
                request.decision(), request.method(), request.reason());
    }

    @PostMapping("/verifications/{teacherId}/revoke")
    public VerificationDecisionView revoke(@PathVariable UUID teacherId,
                                           @Valid @RequestBody VerificationRevokeRequest request) {
        return decisionService.revoke(currentUser.id(), teacherId, request.method(), request.reason());
    }
}
