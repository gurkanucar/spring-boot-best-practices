package com.gucardev.jwtauthrefreshtokenroles.user.service;

import com.gucardev.jwtauthrefreshtokenroles.common.error.BadRequestException;
import com.gucardev.jwtauthrefreshtokenroles.common.error.ConflictException;
import com.gucardev.jwtauthrefreshtokenroles.common.error.ResourceNotFoundException;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpService;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpVerificationResult;
import com.gucardev.jwtauthrefreshtokenroles.token.access.CurrentUser;
import com.gucardev.jwtauthrefreshtokenroles.user.dto.MeResponse;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import com.gucardev.jwtauthrefreshtokenroles.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeService {

    private final UserRepository userRepository;
    private final OtpService otpService;

    /** Answered from the token alone: shows how a resource server reads custom claims. */
    public MeResponse me(Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        return new MeResponse(user.id(), user.email(), user.roles(), user.claims());
    }

    /** Saves the number as pending and texts a code to it; the current verified number stays meanwhile. */
    @Transactional
    public void changePhone(UUID userId, String phone) {
        UserEntity user = findUser(userId);
        if (user.requestPhoneChange(phone)) {
            otpService.issue(user, OtpPurpose.PHONE_VERIFICATION, OtpChannel.SMS);
        }
    }

    // noRollbackFor: a wrong code must still use up one of the code's attempts.
    @Transactional(noRollbackFor = BadRequestException.class)
    public void verifyPhone(UUID userId, String code) {
        if (!(otpService.verifyCode(userId, OtpPurpose.PHONE_VERIFICATION, code) instanceof OtpVerificationResult.Valid)) {
            throw new BadRequestException("Invalid or expired code");
        }
        UserEntity user = findUser(userId);
        // Uniqueness is checked only now, among verified numbers (the unique index is the backstop for races).
        if (userRepository.existsByPhoneAndIdNot(user.getPendingPhone(), userId)) {
            throw new ConflictException("Phone number is already registered to another account");
        }
        user.confirmPendingPhone();
    }

    private UserEntity findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
