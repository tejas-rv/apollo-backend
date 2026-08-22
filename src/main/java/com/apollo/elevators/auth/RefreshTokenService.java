package com.apollo.elevators.auth;

import com.apollo.elevators.common.exception.UnauthorizedException;
import com.apollo.elevators.repository.RefreshTokenRepository;
import com.apollo.elevators.security.SecurityConfigService;
import com.apollo.elevators.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityConfigService securityConfigService;

    @Transactional
    public IssuedRefreshToken issueToken(User user) {
        String plainToken = generatePlainToken();
        Instant expiry = Instant.now().plusMillis(securityConfigService.getJwtRefreshExpirationMs());
        log.info(
                "Issuing refresh token. userId={}, username={}, expiresAt={}",
                user.getId(),
                user.getUsername(),
                expiry
        );

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(plainToken))
                .expiresAt(expiry)
                .build();

        refreshTokenRepository.save(refreshToken);
        log.info("Refresh token issued. tokenId={}, userId={}", refreshToken.getId(), user.getId());

        return new IssuedRefreshToken(plainToken, expiry);
    }

    @Transactional
    public User rotate(String plainToken) {
        String tokenHash = hashToken(plainToken);
        log.info("Rotating refresh token. tokenHashPrefix={}", tokenHash.substring(0, 8));
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid"));

        Instant now = Instant.now();

        if (refreshToken.getRevokedAt() != null || !refreshToken.getExpiresAt().isAfter(now)) {
            log.warn(
                    "Refresh token rejected. tokenId={}, userId={}, revokedAt={}, expiresAt={}, now={}",
                    refreshToken.getId(),
                    refreshToken.getUser().getId(),
                    refreshToken.getRevokedAt(),
                    refreshToken.getExpiresAt(),
                    now
            );
            throw new UnauthorizedException("Refresh token has expired or was revoked");
        }

        refreshToken.setRevokedAt(now);
        refreshTokenRepository.save(refreshToken);
        log.info("Refresh token rotated (revoked old token). tokenId={}, userId={}", refreshToken.getId(), refreshToken.getUser().getId());

        return refreshToken.getUser();
    }

    @Transactional
    public void revokeAllActiveTokens(User user) {
        Instant revokedAt = Instant.now();
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserAndRevokedAtIsNull(user);
        log.info(
                "Revoking all active refresh tokens. userId={}, username={}, activeTokenCount={}",
                user.getId(),
                user.getUsername(),
                activeTokens.size()
        );

        for (RefreshToken token : activeTokens) {
            token.setRevokedAt(revokedAt);
        }

        refreshTokenRepository.saveAll(activeTokens);
        log.info("Revoked all active refresh tokens. userId={}, revokedTokenCount={}", user.getId(), activeTokens.size());
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
