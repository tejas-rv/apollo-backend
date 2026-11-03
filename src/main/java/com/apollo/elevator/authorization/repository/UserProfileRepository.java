package com.apollo.elevator.authorization.repository;

import com.apollo.elevator.authorization.model.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    boolean existsByEmployeeNumberIgnoreCase(String employeeNumber);

    boolean existsByEmployeeNumberIgnoreCaseAndUserIdNot(String employeeNumber, Long userId);
}
