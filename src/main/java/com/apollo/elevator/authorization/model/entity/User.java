package com.apollo.elevator.authorization.model.entity;

import com.apollo.elevator.authorization.model.enums.Role;
import com.apollo.elevator.authorization.model.enums.UserType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_user")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    // BCrypt hash, never store plaintext
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 20)
    private UserType userType;

    private String email;

    private String whatsapp;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    public UserType resolvedUserType() {
        return userType != null ? userType : (role == null ? null : UserType.fromRole(role));
    }
}
