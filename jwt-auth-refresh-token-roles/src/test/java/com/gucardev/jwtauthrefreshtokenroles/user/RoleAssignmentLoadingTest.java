package com.gucardev.jwtauthrefreshtokenroles.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleEntity;
import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleName;
import com.gucardev.jwtauthrefreshtokenroles.role.repository.RoleRepository;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import com.gucardev.jwtauthrefreshtokenroles.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/** Giving a user a role must not load every other user that already has that role. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class RoleAssignmentLoadingTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private void saveUserWithRole() {
        transactionTemplate.executeWithoutResult(status -> {
            UserEntity user = UserEntity.create("load-" + UUID.randomUUID() + "@example.com", "hash", Instant.now());
            user.addRole(roleRepository.findByName(RoleName.USER).orElseThrow());
            userRepository.save(user);
        });
    }

    @Test
    void addingARoleDoesNotFetchTheRolesMembers() {
        for (int i = 0; i < 3; i++) {
            saveUserWithRole();
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        transactionTemplate.executeWithoutResult(status -> {
            RoleEntity role = roleRepository.findByName(RoleName.USER).orElseThrow();
            UserEntity user = UserEntity.create("load-" + UUID.randomUUID() + "@example.com", "hash", Instant.now());
            user.addRole(role);
            userRepository.save(user);
            entityManager.flush();
        });

        assertThat(statistics.getCollectionFetchCount()).isZero();
    }
}
