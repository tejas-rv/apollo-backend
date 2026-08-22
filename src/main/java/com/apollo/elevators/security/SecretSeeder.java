package com.apollo.elevators.security;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class SecretSeeder {

    @Bean
    @Profile("seed") // only runs when you launch with --spring.profiles.active=seed
    public CommandLineRunner seedSecrets(SystemSecretRepository repo, AesEncryptionUtil aes) {
        return args -> {
            save(repo, aes, "jwt.secret", "some-long-random-jwt-signing-secret-min-32-bytes");
            save(repo, aes, "jwt.expiration-ms", "3600000"); // 1 hour
            save(repo, aes, "jwt.refresh-expiration-ms", "2592000000"); // 30 days
            save(repo, aes, "jwt.issuer", "apollo-elevators");
            System.out.println("Secrets seeded.");
        };
    }

    private void save(SystemSecretRepository repo, AesEncryptionUtil aes, String key, String plainValue) {
        SystemSecret secret = repo.findByConfigKey(key).orElse(new SystemSecret());
        secret.setConfigKey(key);
        secret.setConfigValue(aes.encrypt(plainValue));
        repo.save(secret);
    }
}