package com.apollo.elevator.engineer.service;

import com.apollo.elevator.authorization.model.entity.User;
import com.apollo.elevator.authorization.model.entity.UserProfile;
import com.apollo.elevator.authorization.model.enums.Role;
import com.apollo.elevator.authorization.model.enums.UserType;
import com.apollo.elevator.authorization.repository.UserProfileRepository;
import com.apollo.elevator.authorization.repository.UserRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves engineer accounts for AMC service visit assignment. Free-text identifiers
 * (username, full name or employee number) resolve only when they match exactly one engineer.
 */
@Component
@RequiredArgsConstructor
public class EngineerDirectory {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;

    /** Snapshot of all engineer accounts, for resolving many visits at once. */
    @Transactional(readOnly = true)
    public Lookup lookup() {
        List<User> engineers = userRepository.findByRoleOrderByUsernameAsc(Role.ENGINEER).stream()
                .filter(user -> user.resolvedUserType() == UserType.ENGINEER)
                .toList();
        Map<Long, UserProfile> profiles = profileRepository
                .findAllById(engineers.stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity()));

        Map<String, Set<Long>> idsByKey = new HashMap<>();
        Map<Long, String> displayNames = new HashMap<>();
        for (User user : engineers) {
            UserProfile profile = profiles.get(user.getId());
            String fullName = profile == null ? null : profile.getFullName();
            displayNames.put(user.getId(), isBlank(fullName) ? user.getUsername() : fullName.trim());
            index(idsByKey, user.getUsername(), user.getId());
            if (profile != null) {
                index(idsByKey, profile.getFullName(), user.getId());
                index(idsByKey, profile.getEmployeeNumber(), user.getId());
            }
        }
        return new Lookup(idsByKey, displayNames);
    }

    private static void index(Map<String, Set<Long>> idsByKey, String value, Long userId) {
        String key = normalise(value);
        if (key != null) {
            idsByKey.computeIfAbsent(key, ignored -> new HashSet<>()).add(userId);
        }
    }

    static String normalise(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record Lookup(Map<String, Set<Long>> idsByKey, Map<Long, String> displayNames) {

        /** The engineer uniquely identified by username, full name or employee number; otherwise null. */
        public Long resolve(String identifier) {
            String key = normalise(identifier);
            Set<Long> ids = key == null ? null : idsByKey.get(key);
            return ids != null && ids.size() == 1 ? ids.iterator().next() : null;
        }

        public boolean isEngineer(Long userId) {
            return userId != null && displayNames.containsKey(userId);
        }

        public String displayName(Long userId) {
            return displayNames.get(userId);
        }
    }
}
