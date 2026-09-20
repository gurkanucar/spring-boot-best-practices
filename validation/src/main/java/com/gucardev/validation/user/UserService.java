package com.gucardev.validation.user;

import com.gucardev.validation.error.EmailAlreadyExistsException;
import com.gucardev.validation.error.UserNotFoundException;
import com.gucardev.validation.user.dto.AddressRequest;
import com.gucardev.validation.user.dto.CreateUserRequest;
import com.gucardev.validation.user.dto.UnsafeCreateUserRequest;
import com.gucardev.validation.user.dto.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * {@code @Validated} enables method-parameter validation independent of the web
 * layer; a violation raises {@code ConstraintViolationException}.
 */
@Service
@Validated
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * No {@code @Valid} here: it would run {@code @UniqueEmail} before the method
     * body, throwing before the business rule below ever runs.
     */
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }
        User user = toEntity(request.fullName(), request.email(), request.tcKimlikNo(),
                request.age(), request.role(), request.address());
        return toResponse(userRepository.save(user));
    }

    /** Deliberately skips the application-layer email check so the DB constraint fires. */
    public UserResponse createUnsafe(@Valid UnsafeCreateUserRequest request) {
        User user = toEntity(request.fullName(), request.email(), request.tcKimlikNo(),
                request.age(), request.role(), request.address());
        return toResponse(userRepository.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(@Min(value = 1, message = "{validation.param.id.min}") Long id) {
        return userRepository.findById(id)
                .map(UserService::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private static User toEntity(String fullName, String email, String tcKimlikNo,
                                 int age, String role, AddressRequest address) {
        return User.builder()
                .fullName(fullName)
                .email(email)
                .tcKimlikNo(tcKimlikNo)
                .age(age)
                .role(Role.valueOf(role.toUpperCase(Locale.ROOT)))
                .address(new Address(address.city(), address.district(), address.postalCode()))
                .build();
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getAge(), user.getRole());
    }
}
