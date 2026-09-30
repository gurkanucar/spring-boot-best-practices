package com.gucardev.jwtauthrefreshtokenroles.user.entity;

import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * A user. The user-role many-to-many is unidirectional: only this side is mapped, and the
 * {@code user_roles} join table is written from here (see RoleEntity for why there is no inverse side).
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Login name, always stored lower case (see normalizeEmail). */
    @Column(nullable = false, unique = true, length = 320)
    private String email;

    /** BCrypt hash, never the raw password. */
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean emailVerified;

    /**
     * The verified number (E.164, e.g. +905551112233), or null. Unique: only a confirmed number can be
     * someone's, so an unverified entry never blocks the real owner of a number.
     */
    @Column(unique = true, length = 16)
    private String phone;

    /** A number waiting for its SMS code. Not unique: anyone can claim a number until someone proves it. */
    @Column(length = 16)
    private String pendingPhone;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // No cascade on purpose: roles are shared, independent entities.
    // Set, not List: Hibernate then issues targeted inserts/deletes on the join table.
    // BatchSize: load the roles of up to 50 users per query (avoids N+1).
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new HashSet<>();

    // Claims belong to the user: saved and deleted with it.
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserClaimEntity> claims = new HashSet<>();

    public static UserEntity create(String email, String passwordHash, Instant createdAt) {
        UserEntity user = new UserEntity();
        user.setEmail(normalizeEmail(email));
        user.setPassword(passwordHash);
        user.setCreatedAt(createdAt);
        return user;
    }

    /** E-mail addresses are compared case-insensitively: trim and lower-case before storing or looking up. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Read-only view: change roles through addRole/removeRole. */
    public Set<RoleEntity> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public void addRole(RoleEntity role) {
        roles.add(role);
    }

    public void removeRole(RoleEntity role) {
        roles.remove(role);
    }

    /** Role names without prefix, sorted, as they appear in the "roles" JWT claim. */
    public List<String> roleNames() {
        return roles.stream().map(RoleEntity::getName).sorted().toList();
    }

    /** Read-only view: change claims through putClaim/removeClaim. */
    public Set<UserClaimEntity> getClaims() {
        return Collections.unmodifiableSet(claims);
    }

    /** Updates the value in place when the claim exists, so the unique (user_id, claim_name) row is reused. */
    public void putClaim(String name, String value) {
        findClaim(name).ifPresentOrElse(
                claim -> claim.setValue(value),
                () -> claims.add(new UserClaimEntity(this, name, value)));
    }

    public boolean removeClaim(String name) {
        return claims.removeIf(claim -> claim.getName().equals(name));
    }

    public boolean isPhoneVerified() {
        return phone != null;
    }

    /**
     * Stores the number as pending; the verified one (if any) stays until the new one is confirmed.
     * Returns false when the number is already this user's verified number (nothing to do).
     */
    public boolean requestPhoneChange(String newPhone) {
        if (newPhone.equals(phone)) {
            pendingPhone = null;
            return false;
        }
        pendingPhone = newPhone;
        return true;
    }

    /** Called once the SMS code sent to the pending number was confirmed. */
    public void confirmPendingPhone() {
        phone = pendingPhone;
        pendingPhone = null;
    }

    private Optional<UserClaimEntity> findClaim(String name) {
        return claims.stream().filter(claim -> claim.getName().equals(name)).findFirst();
    }
}
