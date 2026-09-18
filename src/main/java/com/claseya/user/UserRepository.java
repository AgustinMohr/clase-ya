package com.claseya.user;

import com.claseya.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSub(String googleSub);

    boolean existsByEmail(String email);
}
