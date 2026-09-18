package com.claseya.favorite.controller;

import com.claseya.favorite.dto.FavoriteResponse;
import com.claseya.favorite.dto.FavoriteStatusResponse;
import com.claseya.favorite.dto.FavoriteTeacherResponse;
import com.claseya.favorite.service.FavoriteService;
import com.claseya.security.CurrentUser;
import com.claseya.teacher.dto.SearchResultPage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final CurrentUser currentUser;

    public FavoriteController(FavoriteService favoriteService, CurrentUser currentUser) {
        this.favoriteService = favoriteService;
        this.currentUser = currentUser;
    }

    @PostMapping("/{teacherId}")
    public ResponseEntity<FavoriteResponse> add(@PathVariable UUID teacherId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(favoriteService.add(currentUser.id(), teacherId));
    }

    @DeleteMapping("/{teacherId}")
    public ResponseEntity<Void> remove(@PathVariable UUID teacherId) {
        favoriteService.remove(currentUser.id(), teacherId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public SearchResultPage<FavoriteTeacherResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return favoriteService.list(currentUser.id(), page, size);
    }

    @GetMapping("/{teacherId}")
    public FavoriteStatusResponse status(@PathVariable UUID teacherId) {
        return favoriteService.status(currentUser.id(), teacherId);
    }
}
