package com.gucardev.jwtauthrefreshtokenroles.user.service;

import com.gucardev.jwtauthrefreshtokenroles.auth.service.PasswordService;
import com.gucardev.jwtauthrefreshtokenroles.common.error.BadRequestException;
import com.gucardev.jwtauthrefreshtokenroles.common.error.ResourceNotFoundException;
import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleEntity;
import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleName;
import com.gucardev.jwtauthrefreshtokenroles.role.repository.RoleRepository;
import com.gucardev.jwtauthrefreshtokenroles.token.access.claims.ClaimRegistry;
import com.gucardev.jwtauthrefreshtokenroles.token.refresh.RefreshTokenStore;
import com.gucardev.jwtauthrefreshtokenroles.user.dto.UserClaimResponse;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import com.gucardev.jwtauthrefreshtokenroles.user.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every change that affects what goes into a token revokes the user's refresh tokens, so the new
 * roles or claims take effect at the next login (at most one access-token lifetime later).
 *
 * <p>The superadmin account is off limits to ADMINs: otherwise an ADMIN could reset the superadmin's
 * password and log in as the superadmin, which would make the role hierarchy meaningless.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Pattern CLAIM_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    private static final String SUPERADMIN_AUTHORITY = "ROLE_" + RoleName.SUPERADMIN;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ClaimRegistry claimRegistry;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordService passwordService;

    /** Idempotent: adding a role the user already has changes nothing. */
    @Transactional
    public void addRole(UUID userId, String roleName) {
        UserEntity user = findManageableUser(userId);
        RoleEntity role = findAssignableRole(roleName);
        if (!user.getRoles().contains(role)) {
            user.addRole(role);
            revokeSessions(user);
        }
    }

    @Transactional
    public void removeRole(UUID userId, String roleName) {
        UserEntity user = findManageableUser(userId);
        RoleEntity role = findAssignableRole(roleName);
        if (!user.getRoles().contains(role)) {
            throw new ResourceNotFoundException("User " + userId + " does not have role " + role.getName());
        }
        user.removeRole(role);
        revokeSessions(user);
    }

    @Transactional(readOnly = true)
    public List<UserClaimResponse> claims(UUID userId) {
        return findManageableUser(userId).getClaims().stream()
                .map(claim -> new UserClaimResponse(claim.getName(), claim.getValue()))
                .sorted(Comparator.comparing(UserClaimResponse::name))
                .toList();
    }

    @Transactional
    public void putClaim(UUID userId, String name, String value) {
        validateClaimName(name);
        UserEntity user = findManageableUser(userId);
        user.putClaim(name, value);
        revokeSessions(user);
    }

    @Transactional
    public void removeClaim(UUID userId, String name) {
        UserEntity user = findManageableUser(userId);
        if (!user.removeClaim(name)) {
            throw new ResourceNotFoundException("User " + userId + " has no claim " + name);
        }
        revokeSessions(user);
    }

    @Transactional
    public void resetPassword(UUID userId, String newPassword) {
        findManageableUser(userId);
        passwordService.adminResetPassword(userId, newPassword);
    }

    private void validateClaimName(String name) {
        if (!CLAIM_NAME.matcher(name).matches()) {
            throw new BadRequestException("Claim name must match " + CLAIM_NAME.pattern());
        }
        if (claimRegistry.isReserved(name)) {
            throw new BadRequestException("Claim name '" + name + "' is reserved");
        }
    }

    private void revokeSessions(UserEntity user) {
        refreshTokenStore.revokeAllForUser(user.getId().toString());
    }

    /** 404 when missing; 403 when the target is the superadmin and the caller is not. */
    private UserEntity findManageableUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.roleNames().contains(RoleName.SUPERADMIN) && !callerIsSuperadmin()) {
            throw new AccessDeniedException("Only the superadmin can manage the superadmin account");
        }
        return user;
    }

    /** The token's own authorities, not the hierarchy: only the superadmin holds ROLE_SUPERADMIN. */
    private static boolean callerIsSuperadmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(SUPERADMIN_AUTHORITY::equals);
    }

    /** SUPERADMIN belongs to the seeded account only and is never assigned or removed here. */
    private RoleEntity findAssignableRole(String roleName) {
        String name = roleName.toUpperCase(Locale.ROOT);
        if (name.equals(RoleName.SUPERADMIN)) {
            throw new BadRequestException("The SUPERADMIN role cannot be assigned or removed");
        }
        return roleRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
    }
}
