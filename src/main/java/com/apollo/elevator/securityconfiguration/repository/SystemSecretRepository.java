package com.apollo.elevator.securityconfiguration.repository;

import com.apollo.elevator.securityconfiguration.model.entity.SystemSecret;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemSecretRepository extends JpaRepository<SystemSecret, String> {
    Optional<SystemSecret> findByConfigKey(String configKey);
}
