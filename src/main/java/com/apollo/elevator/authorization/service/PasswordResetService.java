package com.apollo.elevator.authorization.service;

import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.authorization.model.entity.User;
import com.apollo.elevator.authorization.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void resetPassword(String username, String newPassword) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        String encodedPassword = passwordEncoder.encode(newPassword);

        user.setPassword(encodedPassword);

        userRepository.save(user);
    }
}