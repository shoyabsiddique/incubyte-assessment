package com.acme.salary.auth.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppUserEntityTest {

    @Test
    void storesTheEmailTrimmedAndLowerCasedSoItAlwaysMatchesWhatLoginLooksUp() {
        AppUserEntity user = new AppUserEntity(UUID.randomUUID(), "  Priya.Nair@ACME.example ", "hash", "Priya");

        assertThat(user.getEmail()).isEqualTo("priya.nair@acme.example");
    }
}
