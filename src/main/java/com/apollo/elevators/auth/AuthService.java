package com.apollo.elevators.auth;

import com.apollo.elevators.common.exception.ResourceNotFoundException;
import com.apollo.elevators.common.exception.UnauthorizedException;
import com.apollo.elevators.dto.PasswordResetRequest;
import com.apollo.elevators.dto.auth.CurrentUserResponse;
import com.apollo.elevators.dto.auth.LoginRequest;
import com.apollo.elevators.dto.auth.LoginResponse;
import com.apollo.elevators.dto.auth.RefreshTokenRequest;
import com.apollo.elevators.dto.auth.RefreshTokenResponse;
import com.apollo.elevators.repository.UserRepository;
import com.apollo.elevators.security.JwtService;
import com.apollo.elevators.security.SecurityConfigService;
import com.apollo.elevators.service.PasswordResetService;
import com.apollo.elevators.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SecurityConfigService securityConfigService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("Processing login. username={}", request.username());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (BadCredentialsException exception) {
            log.warn("Login failed due to bad credentials. username={}", request.username());
            throw new UnauthorizedException("Invalid username or password");
        }

        User user = findUserByUsername(request.username());
        String accessToken = jwtService.generateToken(user.getUsername(), user.getRole().name());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issueToken(user);
        log.info(
                "Login successful. userId={}, username={}, role={}, refreshTokenExpiry={}",
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                refreshToken.expiresAt()
        );

        return new LoginResponse(
                accessToken,
                refreshToken.token(),
                user.getUsername(),
                user.getRole().name(),
                securityConfigService.getJwtExpirationMs()
        );
    }

    @Transactional
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        int refreshTokenLength = request.refreshToken() == null ? 0 : request.refreshToken().length();
        log.info("Processing token refresh. refreshTokenLength={}", refreshTokenLength);
        User user = refreshTokenService.rotate(request.refreshToken());
        String accessToken = jwtService.generateToken(user.getUsername(), user.getRole().name());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issueToken(user);
        log.info(
                "Token refresh successful. userId={}, username={}, role={}, refreshTokenExpiry={}",
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                refreshToken.expiresAt()
        );

        return new RefreshTokenResponse(
                accessToken,
                refreshToken.token(),
                securityConfigService.getJwtExpirationMs()
        );
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String username) {
        log.info("Fetching current user profile. username={}", username);
        User user = findUserByUsername(username);
        log.info("Current user profile fetched. userId={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole().name());

        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.getEmail(),
                user.getWhatsapp(),
                user.isEnabled()
        );
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        log.info("Reset password started. username={}", request.getUsername());
        User user = findUserByUsername(request.getUsername());
        log.info("Revoking active refresh tokens before password reset. userId={}, username={}", user.getId(), user.getUsername());
        refreshTokenService.revokeAllActiveTokens(user);
        passwordResetService.resetPassword(request.getUsername(), request.getNewPassword());
        log.info("Password reset completed. userId={}, username={}", user.getId(), user.getUsername());
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found. username={}", username);
                    return new ResourceNotFoundException("User not found: " + username);
                });
    }
}
