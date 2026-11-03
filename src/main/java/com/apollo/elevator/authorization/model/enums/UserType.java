package com.apollo.elevator.authorization.model.enums;

/**
 * Kind of staff account managed from the admin portal.
 * OFFICE (CEO, CXOs and other office bearers) has the same permissions as ADMIN.
 */
public enum UserType {
    ADMIN(Role.ADMIN),
    OFFICE(Role.ADMIN),
    ENGINEER(Role.ENGINEER);

    private final Role securityRole;

    UserType(Role securityRole) {
        this.securityRole = securityRole;
    }

    public Role securityRole() {
        return securityRole;
    }

    /** Fallback for accounts created before user types existed. */
    public static UserType fromRole(Role role) {
        return switch (role) {
            case ADMIN -> ADMIN;
            case ENGINEER -> ENGINEER;
            case CUSTOMER -> null;
        };
    }
}
