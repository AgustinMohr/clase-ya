package com.claseya.user;

import com.claseya.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSub(String googleSub);

    boolean existsByEmail(String email);

    /** AUTH-004: atomic bump; every older JWT stops authenticating. */
    @Modifying
    @Query("update User u set u.sessionVersion = coalesce(u.sessionVersion, 0) + 1 where u.id = :id")
    int incrementSessionVersion(@Param("id") UUID id);
}
