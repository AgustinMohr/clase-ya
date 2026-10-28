package com.claseya.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigOriginsTest {

    @Test
    void trimsEveryCommaSeparatedOrigin() {
        assertThat(SecurityConfig.parseOrigins("https://a.example, https://b.example ,https://c.example"))
                .containsExactly("https://a.example", "https://b.example", "https://c.example");
    }

    @Test
    void ignoresBlankEntries() {
        assertThat(SecurityConfig.parseOrigins(" , ")).isEmpty();
        assertThat(SecurityConfig.parseOrigins("https://a.example,")).containsExactly("https://a.example");
    }
}
