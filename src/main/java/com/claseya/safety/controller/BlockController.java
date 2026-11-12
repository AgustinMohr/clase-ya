package com.claseya.safety.controller;

import com.claseya.safety.dto.BlockedUserResponse;
import com.claseya.safety.service.UserBlockService;
import com.claseya.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** REPORT-001: the blocker always comes from the session; the path only names the target. */
@RestController
@RequestMapping("/api/blocks")
public class BlockController {

    private final UserBlockService userBlockService;
    private final CurrentUser currentUser;

    public BlockController(UserBlockService userBlockService, CurrentUser currentUser) {
        this.userBlockService = userBlockService;
        this.currentUser = currentUser;
    }

    @PostMapping("/{userId}")
    public ResponseEntity<Void> block(@PathVariable UUID userId) {
        userBlockService.block(currentUser.id(), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unblock(@PathVariable UUID userId) {
        userBlockService.unblock(currentUser.id(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<BlockedUserResponse> list() {
        return userBlockService.list(currentUser.id());
    }
}
