package com.acme.salary.auth.application;

import com.acme.salary.auth.domain.JwtSecret;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtSettings.class)
public class JwtConfig {

    public static JwtEncoder encoder(JwtSecret secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key(secret)));
    }

    public static JwtDecoder decoder(JwtSecret secret) {
        return NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static SecretKey key(JwtSecret secret) {
        return new SecretKeySpec(secret.bytes(), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(JwtSettings settings) {
        return encoder(settings.secret());
    }

    @Bean
    JwtDecoder jwtDecoder(JwtSettings settings) {
        return decoder(settings.secret());
    }

    @Bean
    PasswordEncoder passwordEncoder(JwtSettings settings) {
        return new BCryptPasswordEncoder(settings.bcryptStrength());
    }

    @Bean
    TokenService tokenService(JwtEncoder encoder, Clock clock, JwtSettings settings) {
        return new TokenService(encoder, clock, settings.tokenTtl());
    }
}
