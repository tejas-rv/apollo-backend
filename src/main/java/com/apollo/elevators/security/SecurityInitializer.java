package com.apollo.elevators.security;

import com.apollo.elevators.common.Role;
import com.apollo.elevators.user.User;
import com.apollo.elevators.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Runs once at startup:
 *  1. Ensures the JWT signing secret exists in system_secret (generates + encrypts
 *     a random one on first run — you never have to hand-craft it).
 *  2. Ensures at least one ADMIN user exists so you're never locked out.
 *
 * The generated admin password is printed to the console ONCE — copy it and
 * change it immediately via the (to-be-built) user management endpoint.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityInitializer implements ApplicationRunner {

    private final SystemSecretRepository systemSecretRepository;
    private final AesEncryptionUtil aesEncryptionUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityConfigService securityConfigService;

    @Override
    public void run(ApplicationArguments args) {
        ensureSecret(SecurityConfigService.KEY_JWT_SECRET, this::generateRandomSecret, "HMAC signing key for JWTs");
        ensureSecret(SecurityConfigService.KEY_JWT_EXPIRATION_MS, () -> "86400000", "JWT expiry in ms (default 24h)");
        ensureSecret(SecurityConfigService.KEY_JWT_REFRESH_EXPIRATION_MS, () -> "2592000000", "Refresh token expiry in ms (default 30d)");
        ensureSecret(SecurityConfigService.KEY_JWT_ISSUER, () -> "apollo-elevators", "JWT 'iss' claim");

        securityConfigService.refresh();

        ensureDefaultAdmin();
    }

    private void ensureSecret(String key, java.util.function.Supplier<String> valueGenerator, String description) {
        if (systemSecretRepository.findByConfigKey(key).isPresent()) {
            return;
        }
        String plainValue = valueGenerator.get();
        SystemSecret secret = SystemSecret.builder()
                .configKey(key)
                .configValue(aesEncryptionUtil.encrypt(plainValue))
                .description(description)
                .updatedAt(Instant.now())
                .build();
        systemSecretRepository.save(secret);
        log.info("Seeded system_secret entry: {}", key);
    }

    private String generateRandomSecret() {
        byte[] bytes = new byte[64]; // 512-bit, plenty for HS256/HS512
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private void ensureDefaultAdmin() {
        if (userRepository.existsByUsername("admin")) {
            return;
        }
        String generatedPassword = generateRandomPassword();
        User admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode(generatedPassword))
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        userRepository.save(admin);

        log.warn("=============================================================");
        log.warn(" Created default admin user.");
        log.warn(" username: admin");
        log.warn(" password: {}", generatedPassword);
        log.warn(" This password is shown ONCE. Log in and change it immediately.");
        log.warn("=============================================================");
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
