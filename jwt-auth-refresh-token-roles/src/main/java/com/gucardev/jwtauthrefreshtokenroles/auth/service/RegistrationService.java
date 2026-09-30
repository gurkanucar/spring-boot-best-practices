package com.gucardev.jwtauthrefreshtokenroles.auth.service;

import com.gucardev.jwtauthrefreshtokenroles.auth.dto.RegisterRequest;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.RegisterResponse;
import com.gucardev.jwtauthrefreshtokenroles.common.error.ConflictException;
import com.gucardev.jwtauthrefreshtokenroles.common.security.PasswordPolicy;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpService;
import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleName;
import com.gucardev.jwtauthrefreshtokenroles.role.repository.RoleRepository;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import com.gucardev.jwtauthrefreshtokenroles.user.repository.UserRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final OtpService otpService;
    private final Clock clock;

    /** New users get the USER role and cannot log in until the e-mail link is confirmed. */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = UserEntity.normalizeEmail(request.email());
        passwordPolicy.validate(request.password());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("E-mail address is already registered");
        }

        UserEntity user = UserEntity.create(email, passwordEncoder.encode(request.password()), clock.instant());
        // Pending until the SMS code is confirmed; no uniqueness check here, so registering does not
        // reveal which numbers are taken. The clash, if any, surfaces at verification (409).
        user.setPendingPhone(request.phone());
        user.addRole(roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("Role USER is not seeded")));
        userRepository.save(user);

        otpService.issue(user, OtpPurpose.EMAIL_VERIFICATION, OtpChannel.EMAIL);
        if (user.getPendingPhone() != null) {
            otpService.issue(user, OtpPurpose.PHONE_VERIFICATION, OtpChannel.SMS);
        }
        return new RegisterResponse(user.getId(), user.getEmail());
    }
}
