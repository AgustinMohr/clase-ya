package com.claseya.notification.health;

import com.claseya.model.enums.OutboxStatus;
import com.claseya.notification.repository.NotificationOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxHealthIndicatorTest {

    @Mock
    private NotificationOutboxRepository repository;

    @InjectMocks
    private OutboxHealthIndicator indicator;

    @Test
    void isUpWhenThereAreNoDeadRows() {
        when(repository.countByStatus(OutboxStatus.DEAD)).thenReturn(0L);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    void isDegradedAndReportsTheDeadCount() {
        when(repository.countByStatus(OutboxStatus.DEAD)).thenReturn(2L);

        var health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo(OutboxHealthIndicator.DEGRADED);
        assertThat(health.getDetails()).containsEntry("dead", 2L);
    }
}
