package com.claseya.security;

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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
                                                   RestAuthenticationEntryPoint entryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // Stateless JWT API: no session, no CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/test/public").permitAll()
                        .requestMatchers("/api/test/student").hasRole("STUDENT")
                        .requestMatchers("/api/test/teacher").hasRole("TEACHER")
                        .requestMatchers("/api/test/admin").hasRole("ADMIN")
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
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

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
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
