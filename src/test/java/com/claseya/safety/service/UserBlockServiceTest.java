package com.claseya.safety.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.model.User;
import com.claseya.model.UserBlock;
import com.claseya.safety.repository.UserBlockRepository;
import com.claseya.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {

    @Mock
    private UserBlockRepository userBlockRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserBlockService userBlockService;

    @Test
    void rejectsSelfBlock() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> userBlockService.block(userId, userId))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void rejectsAnAlreadyBlockedPair() {
        UUID blockerId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();
        when(userRepository.findById(blockerId)).thenReturn(Optional.of(user(blockerId)));
        when(userRepository.findById(blockedId)).thenReturn(Optional.of(user(blockedId)));
        when(userBlockRepository.existsByBlocker_IdAndBlocked_Id(blockerId, blockedId)).thenReturn(true);

        assertThatThrownBy(() -> userBlockService.block(blockerId, blockedId))
                .isInstanceOf(ConflictException.class);

        verify(userBlockRepository, never()).saveAndFlush(any());
    }

    @Test
    void storesTheBlockWithBothUsers() {
        UUID blockerId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();
        User blocker = user(blockerId);
        User blocked = user(blockedId);
        when(userRepository.findById(blockerId)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(blockedId)).thenReturn(Optional.of(blocked));
        when(userBlockRepository.existsByBlocker_IdAndBlocked_Id(blockerId, blockedId)).thenReturn(false);

        userBlockService.block(blockerId, blockedId);

        ArgumentCaptor<UserBlock> captor = ArgumentCaptor.forClass(UserBlock.class);
        verify(userBlockRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getBlocker()).isSameAs(blocker);
        assertThat(captor.getValue().getBlocked()).isSameAs(blocked);
    }

    @Test
    void unblockDeletesThePair() {
        UUID blockerId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();

        userBlockService.unblock(blockerId, blockedId);

        verify(userBlockRepository).deletePair(blockerId, blockedId);
    }

    @Test
    void listMapsBlockedUsers() {
        UUID blockerId = UUID.randomUUID();
        User blocked = user(UUID.randomUUID());
        UserBlock block = new UserBlock();
        block.setBlocked(blocked);
        when(userBlockRepository.findBlockedByBlockerId(blockerId)).thenReturn(List.of(block));

        var result = userBlockService.list(blockerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(blocked.getId());
        assertThat(result.get(0).displayName()).isEqualTo("User");
    }

    @Test
    void isBlockedEitherWayUsesTheSingleBetweenQuery() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(userBlockRepository.existsBetween(first, second)).thenReturn(true);

        assertThat(userBlockService.isBlockedEitherWay(first, second)).isTrue();
    }

    private static User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setName("User");
        return user;
    }
}
