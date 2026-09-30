package com.claseya.teacher.dto;

/**
 * Announcement completeness (TEACHER-001, RF-2). Purely informative: it never blocks editing or
 * verification; it only tells the teacher whether the announcement is ready to be seen.
 */
public enum AnnouncementCompleteness {
    INCOMPLETE,
    PUBLISHED
}
