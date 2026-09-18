package com.claseya.messaging.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.messaging.dto.ConversationParticipantResponse;
import com.claseya.messaging.dto.ConversationResponse;
import com.claseya.messaging.dto.ConversationSummaryResponse;
import com.claseya.messaging.dto.LastMessageResponse;
import com.claseya.messaging.repository.ConversationParticipantRepository;
import com.claseya.messaging.repository.ConversationRepository;
import com.claseya.messaging.repository.MessageRepository;
import com.claseya.model.Conversation;
import com.claseya.model.ConversationParticipant;
import com.claseya.model.Message;
import com.claseya.model.StudentProfile;
import com.claseya.model.TeacherProfile;
import com.claseya.model.User;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.student.repository.StudentProfileRepository;
import com.claseya.teacher.dto.SearchResultPage;
import com.claseya.teacher.repository.TeacherProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private static final int MAX_SIZE = 50;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationParticipantRepository participantRepository,
                               MessageRepository messageRepository,
                               StudentProfileRepository studentProfileRepository,
                               TeacherProfileRepository teacherProfileRepository) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.teacherProfileRepository = teacherProfileRepository;
    }

    public record ConversationCreated(ConversationResponse conversation, boolean created) {
    }

    @Transactional
    public ConversationCreated start(UUID studentUserId, UUID teacherId) {
        // The caller can only be contacted when publicly visible (Phase 4 rule).
        TeacherProfile teacher = teacherProfileRepository.findById(teacherId)
                .orElseThrow(ResourceNotFoundException::new);
        if (teacher.getVerificationStatus() != VerificationStatus.VERIFIED
                || teacher.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new ResourceNotFoundException();
        }

        StudentProfile student = studentProfileRepository.findByUser_Id(studentUserId)
                .orElseThrow(() -> new ConflictException(
                        "Student profile must be completed before starting a conversation"));

        // One conversation per student<->teacher pair for V1: reuse the existing one.
        List<Conversation> existing =
                conversationRepository.findBetween(studentUserId, teacher.getUser().getId());
        if (!existing.isEmpty()) {
            return new ConversationCreated(toResponse(existing.get(0), studentUserId), false);
        }

        Conversation conversation = new Conversation();
        conversationRepository.saveAndFlush(conversation);
        addParticipant(conversation, student.getUser());
        addParticipant(conversation, teacher.getUser());
        return new ConversationCreated(toResponse(conversation, studentUserId), true);
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(UUID userId, UUID conversationId) {
        Conversation conversation = requireAccessible(userId, conversationId);
        return toResponse(conversation, userId);
    }

    @Transactional(readOnly = true)
    public SearchResultPage<ConversationSummaryResponse> list(UUID userId, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }

        Sort sort = Sort.by(Sort.Order.desc("conversation.updatedAt"), Sort.Order.asc("conversation.id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ConversationParticipant> participations =
                participantRepository.findParticipations(userId, pageable);

        List<UUID> conversationIds = participations.getContent().stream()
                .map(p -> p.getConversation().getId())
                .toList();
        if (conversationIds.isEmpty()) {
            return SearchResultPage.of(List.of(), participations.getNumber(),
                    participations.getSize(), participations.getTotalElements());
        }

        Map<UUID, User> othersByConversation = participantRepository
                .findOthers(conversationIds, userId).stream()
                .collect(Collectors.toMap(p -> p.getConversation().getId(),
                        ConversationParticipant::getUser));
        Map<UUID, Message> lastByConversation = messageRepository
                .findLastPerConversation(conversationIds).stream()
                .collect(Collectors.toMap(m -> m.getConversation().getId(), Function.identity()));
        Map<UUID, Long> unreadByConversation = messageRepository
                .countUnreadGrouped(conversationIds, userId).stream()
                .collect(Collectors.toMap(
                        MessageRepository.UnreadByConversation::getConversationId,
                        MessageRepository.UnreadByConversation::getUnread));

        List<ConversationSummaryResponse> content = participations.getContent().stream()
                .map(participation -> {
                    Conversation conversation = participation.getConversation();
                    User other = othersByConversation.get(conversation.getId());
                    if (other == null) {
                        return null;
                    }
                    Message last = lastByConversation.get(conversation.getId());
                    return new ConversationSummaryResponse(
                            conversation.getId(),
                            ConversationParticipantResponse.from(other),
                            last == null ? null : LastMessageResponse.from(last),
                            unreadByConversation.getOrDefault(conversation.getId(), 0L),
                            conversation.getUpdatedAt());
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return SearchResultPage.of(content, participations.getNumber(),
                participations.getSize(), participations.getTotalElements());
    }

    private ConversationResponse toResponse(Conversation conversation, UUID userId) {
        User other = participantRepository.findOthers(List.of(conversation.getId()), userId).stream()
                .findFirst()
                .map(ConversationParticipant::getUser)
                .orElseThrow(ResourceNotFoundException::new);
        long unread = messageRepository.countUnread(conversation.getId(), userId);
        return new ConversationResponse(conversation.getId(),
                ConversationParticipantResponse.from(other), unread, conversation.getUpdatedAt());
    }

    private void addParticipant(Conversation conversation, User user) {
        ConversationParticipant participant = new ConversationParticipant();
        participant.setConversation(conversation);
        participant.setUser(user);
        participantRepository.saveAndFlush(participant);
    }

    private Conversation requireAccessible(UUID userId, UUID conversationId) {
        if (!participantRepository.existsByConversation_IdAndUser_Id(conversationId, userId)) {
            // 404 hides the existence of conversations the user is not part of.
            throw new ResourceNotFoundException();
        }
        return conversationRepository.findById(conversationId)
                .orElseThrow(ResourceNotFoundException::new);
    }
}
