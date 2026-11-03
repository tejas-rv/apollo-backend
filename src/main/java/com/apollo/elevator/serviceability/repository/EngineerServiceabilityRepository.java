package com.apollo.elevator.serviceability.repository;

import com.apollo.elevator.serviceability.model.entity.EngineerServiceability;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineerServiceabilityRepository extends JpaRepository<EngineerServiceability, Long> {

    List<EngineerServiceability> findAllByEngineerIdIn(List<Long> engineerIds);
}
