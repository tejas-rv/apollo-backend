package com.apollo.elevators.authorization.repository;

import com.apollo.elevators.authorization.model.entity.RefreshToken;
import com.apollo.elevators.authorization.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserAndRevokedAtIsNull(User user);
}
