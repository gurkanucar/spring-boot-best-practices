package com.gucardev.jwtauthrefreshtokenroles.role.repository;

import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    Optional<RoleEntity> findByName(String name);
}
