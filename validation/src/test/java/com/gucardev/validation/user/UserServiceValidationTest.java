package com.gucardev.validation.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.gucardev.validation.error.EmailAlreadyExistsException;
import com.gucardev.validation.error.UserNotFoundException;
import com.gucardev.validation.user.dto.AddressRequest;
import com.gucardev.validation.user.dto.CreateUserRequest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service-layer validation: {@code @Validated} on the class checks method
 * parameters, and a violation raises {@code ConstraintViolationException}.
 */
@SpringBootTest
@Transactional
class UserServiceValidationTest {

    @Autowired
    private UserService userService;

    private static CreateUserRequest request(String email, String tcKimlikNo) {
        return new CreateUserRequest("Ayse Yilmaz", email, tcKimlikNo, 30, "VIEWER",
                new AddressRequest("Ankara", "Cankaya", "06500"));
    }

    @Test
    void createsValidUser() {
        var response = userService.create(request("ayse@example.com", "10000000146"));

        assertThat(response.id()).isNotNull();
        assertThat(response.email()).isEqualTo("ayse@example.com");
    }

    @Test
    void rejectsInvalidIdParameterOnFindById() {
        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> userService.findById(0L));
    }

    @Test
    void throwsWhenUserIsMissing() {
        assertThatExceptionOfType(UserNotFoundException.class)
                .isThrownBy(() -> userService.findById(999_999L));
    }

    @Test
    void rejectsDuplicateEmailAsBusinessRule() {
        userService.create(request("ayse@example.com", "10000000146"));

        assertThatExceptionOfType(EmailAlreadyExistsException.class)
                .isThrownBy(() -> userService.create(request("ayse@example.com", "12345678950")));
    }
}
