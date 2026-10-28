package com.claseya.security;

import com.claseya.security.ratelimit.ClientIpResolver;
import com.claseya.security.ratelimit.RateLimitFilter;
import com.claseya.security.ratelimit.RateLimitProperties;
import com.claseya.security.ratelimit.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtFilter,
                                                   AuthCookieFactory cookieFactory,
                                                   RateLimitProperties rateLimitProperties,
                                                   RateLimitService rateLimitService,
                                                   ClientIpResolver clientIpResolver,
                                                   ObjectMapper objectMapper,
                                                   RestAuthenticationEntryPoint entryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler,
                                                   @Value("${security.csrf.enabled:true}") boolean csrfEnabled) throws Exception {
        // Constructed here (not a Filter bean) so Spring Boot never auto-registers it twice.
        RateLimitFilter rateLimitFilter = new RateLimitFilter(
                rateLimitProperties, rateLimitService, clientIpResolver, objectMapper);

        http
                // Browsers authenticate through the HttpOnly session cookie (SEC-002), so unsafe
                // methods need a CSRF token. Bearer-authenticated requests (integration suite)
                // are exempt because a cross-site page cannot set that header.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "no-referrer"))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()")))
                .authorizeHttpRequests(auth -> auth
                        // /me must be authenticated even though the rest of /api/auth is public.
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        // OPS-001: health is the only operational endpoint exposed.
                        .requestMatchers("/actuator/health").permitAll()
                        // Academic catalog: public reads, admin-only writes.
                        .requestMatchers(HttpMethod.GET,
                                "/api/universities/**",
                                "/api/academic-units/**",
                                "/api/careers/**",
                                "/api/career-subjects/**",
                                "/api/subjects/**").permitAll()
                        .requestMatchers("/api/universities/**",
                                "/api/academic-units/**",
                                "/api/careers/**",
                                "/api/career-subjects/**",
                                "/api/subjects/**").hasRole("ADMIN")
                        .requestMatchers("/api/students/**").hasAnyRole("STUDENT", "ADMIN")
                        .requestMatchers("/api/favorites/**").hasRole("STUDENT")
                        // Messaging: students start conversations; participants read/send.
                        .requestMatchers(HttpMethod.POST, "/api/conversations").hasRole("STUDENT")
                        .requestMatchers("/api/conversations/**").hasAnyRole("STUDENT", "TEACHER")
                        // Own-profile teacher routes stay role-protected even though
                        // public discovery (GET /api/teachers, GET /api/teachers/{id})
                        // is open. Matcher order matters: /me routes come first.
                        .requestMatchers("/api/teachers/me").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers("/api/teachers/me/**").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/teachers", "/api/teachers/*").permitAll()
                        .requestMatchers("/api/teachers/**").hasAnyRole("TEACHER", "ADMIN")
                        // Availability: public discovery is open; publishing is teacher-only.
                        .requestMatchers(HttpMethod.GET, "/api/availability").permitAll()
                        .requestMatchers("/api/availability/**").hasRole("TEACHER")
                        // Verification review queue and decisions: ADMIN only. First use of
                        // ADMIN outside the academic catalog, so it is explicit here.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // RATE-001: after JWT, before authorization, so user-scoped buckets have identity.
                .addFilterBefore(rateLimitFilter, AuthorizationFilter.class);

        if (csrfEnabled) {
            CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();
            csrfTokenRepository.setCookieCustomizer(builder -> builder
                    .path("/")
                    .secure(cookieFactory.isSecure())
                    .sameSite(cookieFactory.getSameSite()));
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .requireCsrfProtectionMatcher(new BearerAwareCsrfMatcher()));
            // Deferred tokens only write the cookie when read; this makes every response carry it.
            http.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
        } else {
            // Integration tests only: the dedicated CSRF test enables it explicitly.
            http.csrf(AbstractHttpConfigurer::disable);
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
