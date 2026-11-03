package com.apollo.elevator.engineer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apollo.elevator.authorization.model.entity.User;
import com.apollo.elevator.authorization.model.entity.UserProfile;
import com.apollo.elevator.authorization.model.enums.Role;
import com.apollo.elevator.authorization.model.enums.UserType;
import com.apollo.elevator.authorization.repository.UserProfileRepository;
import com.apollo.elevator.authorization.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class EngineerDirectoryTest {

    @Test
    void resolves_unique_engineer_by_username_full_name_or_employee_number() {
        UserRepository users = mock(UserRepository.class);
        UserProfileRepository profiles = mock(UserProfileRepository.class);
        User ravi = User.builder().id(7L).username("ravi").role(Role.ENGINEER).userType(UserType.ENGINEER).build();
        User raviS = User.builder().id(8L).username("ravi.s").role(Role.ENGINEER).userType(UserType.ENGINEER).build();
        User amit = User.builder().id(9L).username("amit").role(Role.ENGINEER).userType(UserType.ENGINEER).build();
        when(users.findByRoleOrderByUsernameAsc(Role.ENGINEER)).thenReturn(List.of(ravi, raviS, amit));
        when(profiles.findAllById(any())).thenReturn(List.of(
                UserProfile.builder().userId(7L).fullName("Ravi Kumar").employeeNumber("EMP-007").build(),
                UserProfile.builder().userId(8L).fullName("Ravi  Kumar").build()));

        EngineerDirectory.Lookup lookup = new EngineerDirectory(users, profiles).lookup();

        assertEquals(7L, lookup.resolve(" RAVI "));
        assertEquals(7L, lookup.resolve("emp-007"));
        assertEquals(9L, lookup.resolve("Amit"));
        assertNull(lookup.resolve("ravi kumar"), "names shared by two engineers must stay unresolved");
        assertNull(lookup.resolve("Unknown"));
        assertNull(lookup.resolve(null));
        assertEquals("Ravi Kumar", lookup.displayName(7L));
        assertEquals("amit", lookup.displayName(9L));
        assertTrue(lookup.isEngineer(8L));
        assertFalse(lookup.isEngineer(1L));
    }
}
