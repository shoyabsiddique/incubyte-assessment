package com.acme.salary.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CorsConfigTest {

    @Test
    void splitsAndTrimsTheConfiguredOrigins() {
        assertThat(CorsConfig.parseOrigins(" https://app.example.com , http://localhost:4200 "))
                .containsExactly("https://app.example.com", "http://localhost:4200");
    }

    @Test
    void refusesAnEmptyListSoAMissingSettingCannotSilentlyBlockEveryBrowser() {
        assertThatThrownBy(() -> CorsConfig.parseOrigins("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorsConfig.parseOrigins(" , ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAWildcardOrigin() {
        assertThatThrownBy(() -> CorsConfig.parseOrigins("*"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcard");
        assertThatThrownBy(() -> CorsConfig.parseOrigins("https://a.example,*"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
