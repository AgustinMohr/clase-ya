package com.claseya.safety.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.User;
import com.claseya.model.UserBlock;
import com.claseya.safety.dto.BlockedUserResponse;
import com.claseya.safety.repository.UserBlockRepository;
import com.claseya.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    public UserBlockService(UserBlockRepository userBlockRepository, UserRepository userRepository) {
        this.userBlockRepository = userBlockRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void block(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new BadRequestException("You cannot block yourself");
        }
        User blocker = userRepository.findById(blockerId)
                .orElseThrow(ResourceNotFoundException::new);
        User blocked = userRepository.findById(blockedId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (userBlockRepository.existsByBlocker_IdAndBlocked_Id(blockerId, blockedId)) {
            throw new ConflictException("The user is already blocked");
        }

        UserBlock block = new UserBlock();
        block.setBlocker(blocker);
        block.setBlocked(blocked);
        userBlockRepository.saveAndFlush(block);
    }

    /** Idempotent: unblocking a pair that is not blocked is a successful no-op. */
    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        userBlockRepository.deletePair(blockerId, blockedId);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> list(UUID blockerId) {
        return userBlockRepository.findBlockedByBlockerId(blockerId).stream()
                .map(UserBlock::getBlocked)
                .map(BlockedUserResponse::from)
                .toList();
    }

    /** Both directions count, so a block protects the blocker and the blocked user. */
    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(UUID firstId, UUID secondId) {
        return userBlockRepository.existsBetween(firstId, secondId);
    }
}
