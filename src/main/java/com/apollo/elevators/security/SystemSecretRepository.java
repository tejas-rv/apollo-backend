package com.apollo.elevators.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemSecretRepository extends JpaRepository<SystemSecret, String> {
    Optional<SystemSecret> findByConfigKey(String configKey);
}
