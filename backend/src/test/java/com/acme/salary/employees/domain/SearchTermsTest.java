package com.acme.salary.employees.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchTermsTest {

    @Test
    void wrapsAPlainWordForAContainsMatch() {
        assertThat(SearchTerms.likePattern("priya")).isEqualTo("%priya%");
    }

    @Test
    void matchesPercentUnderscoreAndTheEscapeCharacterLiterally() {
        assertThat(SearchTerms.likePattern("100%")).isEqualTo("%100!%%");
        assertThat(SearchTerms.likePattern("a_b")).isEqualTo("%a!_b%");
        assertThat(SearchTerms.likePattern("hi!")).isEqualTo("%hi!!%");
        assertThat(SearchTerms.likePattern("%")).isEqualTo("%!%%");
    }

    @Test
    void lowerCasesSoTheMatchIgnoresCase() {
        assertThat(SearchTerms.likePattern("PriYa")).isEqualTo("%priya%");
    }

    @Test
    void exposesTheEscapeCharacterTheQueryMustDeclare() {
        assertThat(SearchTerms.ESCAPE).isEqualTo('!');
    }
}
