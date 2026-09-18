package com.claseya.model.enums;

/**
 * Day-part label derived from a window's start time (mañana / tarde / noche).
 * Not persisted; computed for display and as a convenience filter.
 */
public enum AvailabilityDayPart {

    MORNING(0, 720),    // 00:00 <= start < 12:00
    AFTERNOON(720, 1140), // 12:00 <= start < 19:00
    NIGHT(1140, 1440);   // 19:00 <= start

    private final int startInclusive;
    private final int endExclusive;

    AvailabilityDayPart(int startInclusive, int endExclusive) {
        this.startInclusive = startInclusive;
        this.endExclusive = endExclusive;
    }

    public static AvailabilityDayPart fromStartMinutes(int startMinutes) {
        for (AvailabilityDayPart part : values()) {
            if (startMinutes >= part.startInclusive && startMinutes < part.endExclusive) {
                return part;
            }
        }
        return NIGHT;
    }
}
