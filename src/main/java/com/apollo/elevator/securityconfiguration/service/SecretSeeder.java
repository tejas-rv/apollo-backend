package com.apollo.elevator.securityconfiguration.service;

import com.apollo.elevator.securityconfiguration.model.entity.SystemSecret;
import com.apollo.elevator.securityconfiguration.repository.SystemSecretRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Slf4j
public class SecretSeeder {

    private static final String JWT_SECRET = "jwt.secret";
    private static final String JWT_EXPIRATION = "jwt.expiration-ms";
    private static final String JWT_REFRESH_EXPIRATION = "jwt.refresh-expiration-ms";
    private static final String JWT_ISSUER = "jwt.issuer";

    @Bean
    @Profile("seed")
    public CommandLineRunner seedSecrets(SystemSecretRepository repo, AesEncryptionUtil aes) {
        return args -> {
            save(repo, aes, JWT_SECRET, "some-long-random-jwt-signing-secret-min-32-bytes");
            save(repo, aes, JWT_EXPIRATION, "3600000");
            save(repo, aes, JWT_REFRESH_EXPIRATION, "2592000000");
            save(repo, aes, JWT_ISSUER, "apollo-elevator");
            log.info("JWT secrets seeded");
        };
    }

    private void save(SystemSecretRepository repo, AesEncryptionUtil aes, String key, String plainValue) {
        SystemSecret secret = repo.findByConfigKey(key).orElseGet(SystemSecret::new);
        secret.setConfigKey(key);
        secret.setConfigValue(aes.encrypt(plainValue));
        repo.save(secret);
    }
}