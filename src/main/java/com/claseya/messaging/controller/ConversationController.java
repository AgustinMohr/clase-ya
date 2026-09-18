package com.claseya.messaging.controller;

import com.claseya.messaging.dto.ConversationResponse;
import com.claseya.messaging.dto.ConversationSummaryResponse;
import com.claseya.messaging.dto.CreateConversationRequest;
import com.claseya.messaging.dto.MessageResponse;
import com.claseya.messaging.dto.SendMessageRequest;
import com.claseya.messaging.service.ConversationService;
import com.claseya.messaging.service.MessageService;
import com.claseya.security.CurrentUser;
import com.claseya.teacher.dto.SearchResultPage;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final CurrentUser currentUser;

    public ConversationController(ConversationService conversationService,
                                  MessageService messageService,
                                  CurrentUser currentUser) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> start(
            @Valid @RequestBody CreateConversationRequest request) {
        ConversationService.ConversationCreated result =
                conversationService.start(currentUser.id(), request.teacherId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.conversation());
    }

    @GetMapping
    public SearchResultPage<ConversationSummaryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return conversationService.list(currentUser.id(), page, size);
    }

    @GetMapping("/{conversationId}")
    public ConversationResponse get(@PathVariable UUID conversationId) {
        return conversationService.get(currentUser.id(), conversationId);
    }

    @GetMapping("/{conversationId}/messages")
    public SearchResultPage<MessageResponse> messages(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return messageService.list(currentUser.id(), conversationId, page, size);
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageResponse> send(@PathVariable UUID conversationId,
                                                @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.send(currentUser.id(), conversationId, request));
    }

    @PatchMapping("/{conversationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID conversationId) {
        messageService.markRead(currentUser.id(), conversationId);
        return ResponseEntity.noContent().build();
    }
}
