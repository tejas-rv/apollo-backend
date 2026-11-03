package com.apollo.elevator.serviceability.repository;

import com.apollo.elevator.customer.model.entity.ServiceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceHistoryRepository extends JpaRepository<ServiceHistory, Long> {
}
