package com.apollo.elevators.auth;

import com.apollo.elevators.auth.dto.CurrentUserResponse;
import com.apollo.elevators.auth.dto.LoginRequest;
import com.apollo.elevators.auth.dto.LoginResponse;
import com.apollo.elevators.auth.dto.RefreshTokenRequest;
import com.apollo.elevators.auth.dto.RefreshTokenResponse;
import com.apollo.elevators.common.exception.ResourceNotFoundException;
import com.apollo.elevators.common.exception.UnauthorizedException;
import com.apollo.elevators.dto.PasswordResetRequest;
import com.apollo.elevators.repository.UserRepository;
import com.apollo.elevators.security.JwtService;
import com.apollo.elevators.security.SecurityConfigService;
import com.apollo.elevators.service.PasswordResetService;
import com.apollo.elevators.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SecurityConfigService securityConfigService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (BadCredentialsException exception) {
            throw new UnauthorizedException("Invalid username or password");
        }

        User user = findUserByUsername(request.username());
        String accessToken = jwtService.generateToken(user.getUsername(), user.getRole().name());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issueToken(user);

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
        User user = refreshTokenService.rotate(request.refreshToken());
        String accessToken = jwtService.generateToken(user.getUsername(), user.getRole().name());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issueToken(user);

        return new RefreshTokenResponse(
                accessToken,
                refreshToken.token(),
                securityConfigService.getJwtExpirationMs()
        );
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String username) {
        User user = findUserByUsername(username);

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
        User user = findUserByUsername(request.getUsername());
        refreshTokenService.revokeAllActiveTokens(user);
        passwordResetService.resetPassword(request.getUsername(), request.getNewPassword());
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
