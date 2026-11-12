package com.claseya.auth.service;

import com.claseya.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** AUTH-004: revokes every JWT issued before the call by bumping the user's session version. */
@Service
public class SessionService {

    private final UserRepository userRepository;

    public SessionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void invalidateAllSessions(UUID userId) {
        userRepository.incrementSessionVersion(userId);
    }
}
