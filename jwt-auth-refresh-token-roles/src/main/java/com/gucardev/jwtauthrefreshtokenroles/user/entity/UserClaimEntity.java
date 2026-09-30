package com.gucardev.jwtauthrefreshtokenroles.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A custom JWT claim an admin attached to one user. Values are plain strings. */
@Getter
@Setter
@Entity
@Table(name = "user_claims",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_claims_user_name", columnNames = {"user_id", "claim_name"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserClaimEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    // "name"/"value" would be fine in PostgreSQL, but VALUE is a keyword in H2.
    @Column(name = "claim_name", nullable = false, length = 64)
    private String name;

    @Column(name = "claim_value", nullable = false, length = 255)
    private String value;

    public UserClaimEntity(UserEntity user, String name, String value) {
        this.user = user;
        this.name = name;
        this.value = value;
    }
}
