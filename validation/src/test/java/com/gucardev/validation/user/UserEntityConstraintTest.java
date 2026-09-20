package com.gucardev.validation.user;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Verifies database-level constraints, bypassing DTO validation, to show what
 * the database enforces on its own when the application layer is skipped.
 */
@DataJpaTest
class UserEntityConstraintTest {

    @Autowired
    private UserRepository userRepository;

    private static User validUser(String email, String tcKimlikNo, int age) {
        return User.builder()
                .fullName("Ayse Yilmaz")
                .email(email)
                .tcKimlikNo(tcKimlikNo)
                .age(age)
                .role(Role.VIEWER)
                .address(new Address("Ankara", "Cankaya", "06500"))
                .build();
    }

    @Test
    void persistsValidUser() {
        assertThatNoException().isThrownBy(() ->
                userRepository.saveAndFlush(validUser("ayse@example.com", "10000000146", 30)));
    }

    @Test
    void rejectsDuplicateEmailWithUniqueConstraint() {
        userRepository.saveAndFlush(validUser("ayse@example.com", "10000000146", 30));

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                userRepository.saveAndFlush(validUser("ayse@example.com", "12345678950", 30)));
    }

    @Test
    void rejectsUnderageUserWithCheckConstraint() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                userRepository.saveAndFlush(validUser("cocuk@example.com", "10000000146", 15)));
    }

    @Test
    void rejectsInvalidEmailBeforeReachingDatabase() {
        // Entity-level jakarta annotations run before insert, so the DB is never touched.
        assertThatExceptionOfType(jakarta.validation.ConstraintViolationException.class).isThrownBy(() ->
                userRepository.saveAndFlush(validUser("gecersiz-eposta", "10000000146", 30)));
    }
}
