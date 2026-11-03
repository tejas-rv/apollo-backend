package com.apollo.elevator.authorization.repository;

import com.apollo.elevator.authorization.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import com.apollo.elevator.authorization.model.enums.Role;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByRoleOrderByUsernameAsc(Role role);
    List<User> findByRoleInOrderByUsernameAsc(Collection<Role> roles);
    long countByRoleAndEnabledTrue(Role role);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
