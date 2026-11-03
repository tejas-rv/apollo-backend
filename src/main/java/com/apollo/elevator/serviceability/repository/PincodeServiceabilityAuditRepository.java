package com.apollo.elevator.serviceability.repository;

import com.apollo.elevator.serviceability.model.entity.PincodeServiceabilityAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PincodeServiceabilityAuditRepository extends JpaRepository<PincodeServiceabilityAudit, Long> {
}
