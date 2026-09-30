package com.gucardev.jwtauthrefreshtokenroles.common.config;

import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleEntity;
import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleName;
import com.gucardev.jwtauthrefreshtokenroles.role.repository.RoleRepository;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import com.gucardev.jwtauthrefreshtokenroles.user.repository.UserRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the USER, ADMIN and SUPERADMIN roles and the single SUPERADMIN account. Safe to run on every
 * start: once any account holds SUPERADMIN, no second one is created, even if app.seed.admin-email changes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureRole(RoleName.USER);
        ensureRole(RoleName.ADMIN);
        RoleEntity superadminRole = ensureRole(RoleName.SUPERADMIN);

        String email = UserEntity.normalizeEmail(appProperties.seed().adminEmail());
        if (userRepository.existsByRolesName(RoleName.SUPERADMIN) || userRepository.existsByEmail(email)) {
            return;
        }
        UserEntity superadmin = UserEntity.create(
                email, passwordEncoder.encode(appProperties.seed().adminPassword()), clock.instant());
        superadmin.setEmailVerified(true);
        // USER and ADMIN come from the role hierarchy.
        superadmin.addRole(superadminRole);
        userRepository.save(superadmin);
        log.info("Seeded superadmin {}", email);
    }

    private RoleEntity ensureRole(String name) {
        return roleRepository.findByName(name).orElseGet(() -> roleRepository.save(new RoleEntity(name)));
    }
}
