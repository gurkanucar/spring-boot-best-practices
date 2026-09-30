package com.gucardev.jwtauthrefreshtokenroles.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleName;
import com.gucardev.jwtauthrefreshtokenroles.role.repository.RoleRepository;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import com.gucardev.jwtauthrefreshtokenroles.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class DataSeederTest {

    @Autowired
    private DataSeeder dataSeeder;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void seedsRolesAndTheSingleVerifiedSuperadmin() {
        assertThat(roleRepository.findByName(RoleName.USER)).isPresent();
        assertThat(roleRepository.findByName(RoleName.ADMIN)).isPresent();
        assertThat(roleRepository.findByName(RoleName.SUPERADMIN)).isPresent();

        transactionTemplate.executeWithoutResult(status -> {
            UserEntity admin = userRepository.findByEmail("admin@example.com").orElseThrow();
            assertThat(admin.isEmailVerified()).isTrue();
            // ADMIN and USER come from the role hierarchy, not from extra rows in user_roles.
            assertThat(admin.roleNames()).containsExactly("SUPERADMIN");
            assertThat(passwordEncoder.matches("admin12345", admin.getPassword())).isTrue();
            assertThat(admin.getPassword()).doesNotContain("admin12345");
        });
    }

    @Test
    void isIdempotent() {
        long roles = roleRepository.count();
        long users = userRepository.count();

        dataSeeder.run(new DefaultApplicationArguments());

        assertThat(roleRepository.count()).isEqualTo(roles);
        assertThat(userRepository.count()).isEqualTo(users);
    }
}
