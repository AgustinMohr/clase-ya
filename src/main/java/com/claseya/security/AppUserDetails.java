package com.claseya.security;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security principal wrapping a ClaseYa {@link User}.
 *
 * <p>Role is exposed as an authority with the {@code ROLE_} prefix
 * ({@code ROLE_STUDENT}, {@code ROLE_TEACHER}, {@code ROLE_ADMIN}).
 * Only {@link UserStatus#ACTIVE} accounts are enabled.
 */
public class AppUserDetails implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final UserStatus status;

    public AppUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.status = user.getStatus();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
