package com.apollo.elevator.serviceability.repository;

import com.apollo.elevator.serviceability.model.entity.ServiceabilityZone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceabilityZoneRepository extends JpaRepository<ServiceabilityZone, String> {
}
