package com.gucardev.jwtauthrefreshtokenroles.role.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A role. The user-role many-to-many is mapped only on {@code UserEntity.roles} (unidirectional):
 * an inverse {@code Set<UserEntity> users} here would have to be kept in sync on every role change,
 * and adding one element to that lazy set loads every user who already has the role — with many
 * users, every registration would read the whole USER membership. Nothing needs "users of a role"
 * as a collection; a repository query does that job when it is needed.
 */
@Getter
@Setter
@Entity
@Table(name = "roles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    public RoleEntity(String name) {
        this.name = name;
    }
}
