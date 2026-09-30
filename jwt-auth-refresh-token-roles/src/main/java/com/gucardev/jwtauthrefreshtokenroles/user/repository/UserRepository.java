package com.gucardev.jwtauthrefreshtokenroles.user.repository;

import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /** Expects an already normalised (lower-case) e-mail. */
    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRolesName(String roleName);

    boolean existsByPhoneAndIdNot(String phone, UUID id);

    /** SELECT ... FOR UPDATE: serialises concurrent work on one user (e.g. a double-clicked "forgot password"). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> lockById(@Param("id") UUID id);
}
