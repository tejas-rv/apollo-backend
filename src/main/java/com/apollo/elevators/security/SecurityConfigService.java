package com.apollo.elevators.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads JWT-related config (secret key, expiry, issuer) from the database,
 * decrypting sensitive values via AesEncryptionUtil, and caches them in memory
 * so we don't hit the DB on every request.
 *
 * Call refresh() (e.g. from an admin-only endpoint) after rotating a secret
 * in the database to pick up the change without restarting the app.
 */
@Service
@RequiredArgsConstructor
public class SecurityConfigService {

    public static final String KEY_JWT_SECRET = "jwt.secret";
    public static final String KEY_JWT_EXPIRATION_MS = "jwt.expiration-ms";
    public static final String KEY_JWT_REFRESH_EXPIRATION_MS = "jwt.refresh-expiration-ms";
    public static final String KEY_JWT_ISSUER = "jwt.issuer";

    private final SystemSecretRepository systemSecretRepository;
    private final AesEncryptionUtil aesEncryptionUtil;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public synchronized void refresh() {
        cache.clear();
        systemSecretRepository.findAll().forEach(secret ->
            cache.put(secret.getConfigKey(), aesEncryptionUtil.decrypt(secret.getConfigValue()))
        );
    }

    private String get(String key) {
        if (cache.isEmpty()) {
            refresh();
        }
        String value = cache.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing required system_secret entry: " + key);
        }
        return value;
    }

    public String getJwtSecret() {
        return get(KEY_JWT_SECRET);
    }

    public long getJwtExpirationMs() {
        return Long.parseLong(get(KEY_JWT_EXPIRATION_MS));
    }

    public long getJwtRefreshExpirationMs() {
        return Long.parseLong(get(KEY_JWT_REFRESH_EXPIRATION_MS));
    }

    public String getJwtIssuer() {
        return get(KEY_JWT_ISSUER);
    }
}
