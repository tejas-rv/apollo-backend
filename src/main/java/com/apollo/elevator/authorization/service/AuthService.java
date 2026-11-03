package com.apollo.elevator.authorization.service;

import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.common.exception.UnauthorizedException;
import com.apollo.elevator.common.util.LogMaskingUtil;
import com.apollo.elevator.authorization.model.dto.ChangePasswordRequest;
import com.apollo.elevator.authorization.model.dto.PasswordResetRequest;
import com.apollo.elevator.authorization.model.dto.CurrentUserResponse;
import com.apollo.elevator.authorization.model.dto.LoginRequest;
import com.apollo.elevator.authorization.model.dto.LoginResponse;
import com.apollo.elevator.authorization.model.dto.RefreshTokenRequest;
import com.apollo.elevator.authorization.model.dto.RefreshTokenResponse;
import com.apollo.elevator.authorization.model.enums.UserType;
import com.apollo.elevator.authorization.repository.UserRepository;
import com.apollo.elevator.securityconfiguration.service.JwtService;
import com.apollo.elevator.securityconfiguration.service.SecurityConfigService;
import com.apollo.elevator.authorization.service.PasswordResetService;
import com.apollo.elevator.authorization.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("Processing login. username={}", LogMaskingUtil.mask("username", request.username()));
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (BadCredentialsException exception) {
            log.warn("Login failed due to bad credentials. username={}",
                    LogMaskingUtil.mask("username", request.username()));
            throw new UnauthorizedException("Invalid username or password");
        }

        User user = findUserByUsername(request.username());
        String accessToken = jwtService.generateToken(user.getUsername(), user.getRole().name());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issueToken(user);
        log.info(
                "Login successful. userId={}, username={}, role={}, refreshTokenExpiry={}",
                user.getId(),
                LogMaskingUtil.mask("username", user.getUsername()),
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
        log.info("Fetching current user profile. username={}", LogMaskingUtil.mask("username", username));
        User user = findUserByUsername(username);
        log.info("Current user profile fetched. userId={}, username={}, role={}",
                user.getId(), LogMaskingUtil.mask("username", user.getUsername()), user.getRole().name());

        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.resolvedUserType() == null ? null : user.resolvedUserType().name(),
                user.getEmail(),
                user.getWhatsapp(),
                user.isEnabled()
        );
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        log.info("Reset password started. username={}", LogMaskingUtil.mask("username", request.getUsername()));
        User user = findUserByUsername(request.getUsername());
        log.info("Revoking active refresh tokens before password reset. userId={}, username={}",
                user.getId(), LogMaskingUtil.mask("username", user.getUsername()));
        refreshTokenService.revokeAllActiveTokens(user);
        passwordResetService.resetPassword(request.getUsername(), request.getNewPassword());
        log.info("Password reset completed. userId={}, username={}",
                user.getId(), LogMaskingUtil.mask("username", user.getUsername()));
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        log.info("Change password started. username={}", LogMaskingUtil.mask("username", username));
        User user = findUserByUsername(username);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            log.warn("Change password failed due to incorrect current password. username={}",
                    LogMaskingUtil.mask("username", username));
            throw new UnauthorizedException("Current password is incorrect");
        }
        refreshTokenService.revokeAllActiveTokens(user);
        passwordResetService.resetPassword(username, request.newPassword());
        log.info("Change password completed. userId={}, username={}",
                user.getId(), LogMaskingUtil.mask("username", user.getUsername()));
    }

    /** Creates the staff login only; profile data is owned by UserManagementService. */
    @Transactional
    public User createAccount(String rawUsername, String rawPassword, UserType userType) {
        String username = rawUsername == null ? "" : rawUsername.trim();
        String password = rawPassword == null ? "" : rawPassword;

        if (username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        if (userType == null) {
            throw new IllegalArgumentException("userType is required");
        }
        if (userRepository.existsByUsername(username)) {
            log.warn("User creation failed due to duplicate username. username={}",
                    LogMaskingUtil.mask("username", username));
            throw new ConflictException("Username already exists: " + username);
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .role(userType.securityRole())
                .userType(userType)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("User created. userId={}, username={}, userType={}, role={}",
                saved.getId(), LogMaskingUtil.mask("username", saved.getUsername()), userType, saved.getRole().name());

        return saved;
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found. username={}", LogMaskingUtil.mask("username", username));
                    return new ResourceNotFoundException("User not found: " + username);
                });
    }
}
