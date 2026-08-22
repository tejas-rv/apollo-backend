package com.apollo.elevators.auth;

import com.apollo.elevators.common.exception.UnauthorizedException;
import com.apollo.elevators.repository.RefreshTokenRepository;
import com.apollo.elevators.security.SecurityConfigService;
import com.apollo.elevators.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityConfigService securityConfigService;

    @Transactional
    public IssuedRefreshToken issueToken(User user) {
        String plainToken = generatePlainToken();
        Instant expiry = Instant.now().plusMillis(securityConfigService.getJwtRefreshExpirationMs());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(plainToken))
                .expiresAt(expiry)
                .build();

        refreshTokenRepository.save(refreshToken);

        return new IssuedRefreshToken(plainToken, expiry);
    }

    @Transactional
    public User rotate(String plainToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashToken(plainToken))
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid"));

        Instant now = Instant.now();

        if (refreshToken.getRevokedAt() != null || !refreshToken.getExpiresAt().isAfter(now)) {
            throw new UnauthorizedException("Refresh token has expired or was revoked");
        }

        refreshToken.setRevokedAt(now);
        refreshTokenRepository.save(refreshToken);

        return refreshToken.getUser();
    }

    @Transactional
    public void revokeAllActiveTokens(User user) {
        Instant revokedAt = Instant.now();
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserAndRevokedAtIsNull(user);

        for (RefreshToken token : activeTokens) {
            token.setRevokedAt(revokedAt);
        }

        refreshTokenRepository.saveAll(activeTokens);
    }

    private String generatePlainToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String plainToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(plainToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    public record IssuedRefreshToken(String token, Instant expiresAt) {}
}
