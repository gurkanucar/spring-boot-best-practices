package com.gucardev.jwtauthrefreshtokenroles.role.entity;

/**
 * Role names as stored in the roles table; Spring Security sees them with a ROLE_ prefix.
 * Hierarchy (SecurityConfig.roleHierarchy): SUPERADMIN > ADMIN > USER. SUPERADMIN belongs to exactly
 * one account, the seeded one, and cannot be assigned or removed through the API.
 */
public final class RoleName {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";
    public static final String SUPERADMIN = "SUPERADMIN";

    private RoleName() {
    }
}
