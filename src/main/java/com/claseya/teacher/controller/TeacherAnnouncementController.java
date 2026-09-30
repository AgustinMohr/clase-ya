package com.claseya.teacher.controller;

import com.claseya.security.CurrentUser;
import com.claseya.teacher.dto.AnnouncementStatusResponse;
import com.claseya.teacher.service.AnnouncementCompletenessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Mi anuncio" completeness (RF-2). Informative only; never blocks editing or verification. */
@RestController
@RequestMapping("/api/teachers/me/announcement")
public class TeacherAnnouncementController {

    private final AnnouncementCompletenessService announcementCompletenessService;
    private final CurrentUser currentUser;

    public TeacherAnnouncementController(AnnouncementCompletenessService announcementCompletenessService,
                                         CurrentUser currentUser) {
        this.announcementCompletenessService = announcementCompletenessService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public AnnouncementStatusResponse status() {
        return announcementCompletenessService.statusFor(currentUser.id());
    }
}
