package com.claseya.availability;

import com.claseya.availability.service.AvailabilityService;
import com.claseya.model.enums.AvailabilityDayPart;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityValidationTest {

    @Test
    void minutesConversions_areCorrect() {
        assertThat(AvailabilityService.startMinutesOf("09:00")).isEqualTo(540);
        assertThat(AvailabilityService.startMinutesOf("18:00")).isEqualTo(1080);
        assertThat(AvailabilityService.startMinutesOf("23:59")).isEqualTo(1439);
        assertThat(AvailabilityService.endMinutesOf("20:30")).isEqualTo(1230);
        // "00:00" used as an end means end of day.
        assertThat(AvailabilityService.endMinutesOf("00:00")).isEqualTo(1440);
    }

    @Test
    void dayPart_isDerivedFromStartHour() {
        assertThat(AvailabilityDayPart.fromStartMinutes(8 * 60)).isEqualTo(AvailabilityDayPart.MORNING);
        assertThat(AvailabilityDayPart.fromStartMinutes(11 * 60 + 59)).isEqualTo(AvailabilityDayPart.MORNING);
        assertThat(AvailabilityDayPart.fromStartMinutes(12 * 60)).isEqualTo(AvailabilityDayPart.AFTERNOON);
        assertThat(AvailabilityDayPart.fromStartMinutes(18 * 60 + 59)).isEqualTo(AvailabilityDayPart.AFTERNOON);
        assertThat(AvailabilityDayPart.fromStartMinutes(19 * 60)).isEqualTo(AvailabilityDayPart.NIGHT);
    }
}
