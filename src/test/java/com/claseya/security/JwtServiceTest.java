package com.claseya.security;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-thirty-two-bytes-long-for-hmac-sha-256";
    private static final long EXPIRATION = 3600;

    private JwtService service() {
        return new JwtService(SECRET, EXPIRATION);
    }

    private AppUserDetails user(UUID id, String email, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setPasswordHash("$2a$10$not-a-real-hash-value");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        return new AppUserDetails(u);
    }

    @Test
    void generateThenParse_returnsExpectedClaims() {
        UUID id = UUID.randomUUID();
        AppUserDetails user = user(id, "jane@example.com", UserRole.TEACHER);

        String token = service().generateToken(user);
        Claims claims = service().parseToken(token);

        assertEquals(id.toString(), claims.getSubject());
        assertEquals("jane@example.com", claims.get("email", String.class));
        assertEquals("TEACHER", claims.get("role", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void parseToken_expired_throws() {
        AppUserDetails user = user(UUID.randomUUID(), "a@example.com", UserRole.STUDENT);
        String expired = Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(ExpiredJwtException.class, () -> service().parseToken(expired));
    }

    @Test
    void parseToken_tampered_throws() {
        AppUserDetails user = user(UUID.randomUUID(), "a@example.com", UserRole.STUDENT);
        String token = service().generateToken(user);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThrows(JwtException.class, () -> service().parseToken(tampered));
    }

    @Test
    void parseToken_signedWithWrongKey_throws() {
        AppUserDetails user = user(UUID.randomUUID(), "a@example.com", UserRole.STUDENT);
        String other = "a-completely-different-secret-key-that-is-also-long-enough-for-hmac";
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(other.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(JwtException.class, () -> service().parseToken(token));
    }

    @Test
    void parseToken_withoutSubject_allowsNullSubject() {
        String token = Jwts.builder()
                .claim("email", "x@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertNull(service().parseToken(token).getSubject());
    }
}
