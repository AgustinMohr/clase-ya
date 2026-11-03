package com.claseya.auth.service;

import com.claseya.auth.dto.IssuedSession;
import com.claseya.auth.dto.SessionUserResponse;
import com.claseya.common.exception.BadRequestException;
import com.claseya.model.User;
import com.claseya.model.enums.UserStatus;
import com.claseya.notification.service.MessageLinkTokenService;
import com.claseya.notification.service.MessageLinkTokenService.MessageLinkTarget;
import com.claseya.security.AppUserDetails;
import com.claseya.security.JwtService;
import com.claseya.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Exchanges a single-use message link for a normal session cookie (REVIEW2-002).
 * Every failure mode returns the same generic message, so a probe cannot tell an expired link from
 * an unknown one or from a suspended account.
 */
@Service
public class MessageLinkAuthService {

    private final MessageLinkTokenService linkTokens;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public MessageLinkAuthService(MessageLinkTokenService linkTokens,
                                  UserRepository userRepository,
                                  JwtService jwtService) {
        this.linkTokens = linkTokens;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public IssuedSession exchange(String rawToken) {
        MessageLinkTarget target = linkTokens.consume(rawToken).orElseThrow(MessageLinkAuthService::invalid);
        User user = userRepository.findById(target.userId()).orElseThrow(MessageLinkAuthService::invalid);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalid();
        }
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String token = jwtService.generateToken(new AppUserDetails(user));
        return new IssuedSession(token, SessionUserResponse.from(user), jwtService.getExpirationSeconds());
    }

    private static BadRequestException invalid() {
        return new BadRequestException("The link is invalid or has expired");
    }
}
