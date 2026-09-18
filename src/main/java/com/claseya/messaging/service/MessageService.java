package com.claseya.messaging.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.messaging.dto.MessageResponse;
import com.claseya.messaging.dto.SendMessageRequest;
import com.claseya.messaging.repository.ConversationParticipantRepository;
import com.claseya.messaging.repository.ConversationRepository;
import com.claseya.messaging.repository.MessageRepository;
import com.claseya.model.Conversation;
import com.claseya.model.Message;
import com.claseya.model.User;
import com.claseya.teacher.dto.SearchResultPage;
import com.claseya.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    private static final int MAX_SIZE = 100;
    private static final int MAX_CONTENT = 5000;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          ConversationParticipantRepository participantRepository,
                          UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public SearchResultPage<MessageResponse> list(UUID userId, UUID conversationId, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
        requireAccessible(userId, conversationId);

        Sort sort = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Message> messages = messageRepository.findWithSenderByConversation(conversationId, pageable);

        List<MessageResponse> content = messages.getContent().stream()
                .map(MessageResponse::from)
                .toList();
        return SearchResultPage.of(content, messages.getNumber(), messages.getSize(),
                messages.getTotalElements());
    }

    @Transactional
    public MessageResponse send(UUID userId, UUID conversationId, SendMessageRequest request) {
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw new BadRequestException("Content is required");
        }
        if (content.length() > MAX_CONTENT) {
            throw new BadRequestException("Content must be at most " + MAX_CONTENT + " characters");
        }
        requireAccessible(userId, conversationId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(ResourceNotFoundException::new);
        User sender = userRepository.findById(userId)
                .orElseThrow(ResourceNotFoundException::new);

        // Bump the conversation so the list is ordered by recent activity.
        conversation.setUpdatedAt(Instant.now());

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content);
        Message saved = messageRepository.saveAndFlush(message);
        return MessageResponse.from(saved);
    }

    @Transactional
    public void markRead(UUID userId, UUID conversationId) {
        requireAccessible(userId, conversationId);
        messageRepository.markConversationRead(conversationId, userId, Instant.now());
    }

    private void requireAccessible(UUID userId, UUID conversationId) {
        if (!participantRepository.existsByConversation_IdAndUser_Id(conversationId, userId)) {
            throw new ResourceNotFoundException();
        }
    }
}
