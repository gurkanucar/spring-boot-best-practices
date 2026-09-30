package com.gucardev.jwtauthrefreshtokenroles.auth.service;

import com.gucardev.jwtauthrefreshtokenroles.auth.dto.ChangePasswordRequest;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.ForgotPasswordRequest;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.ResetPasswordRequest;
import com.gucardev.jwtauthrefreshtokenroles.common.error.BadRequestException;
import com.gucardev.jwtauthrefreshtokenroles.common.error.ResourceNotFoundException;
import com.gucardev.jwtauthrefreshtokenroles.common.security.PasswordPolicy;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpService;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpVerificationResult;
import com.gucardev.jwtauthrefreshtokenroles.token.refresh.RefreshTokenStore;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import com.gucardev.jwtauthrefreshtokenroles.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every successful password change ends all refresh-token sessions of the user. */
@Service
@RequiredArgsConstructor
public class PasswordService {

    private static final String INVALID_CODE = "Invalid or expired code";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final OtpService otpService;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        UserEntity user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        passwordPolicy.validate(request.newPassword());
        setPassword(user, request.newPassword());
    }

    /** Silent: sends nothing for unknown users, or over SMS when the phone is not verified. */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(UserEntity.normalizeEmail(request.email()))
                .filter(user -> request.channel() == OtpChannel.EMAIL || user.isPhoneVerified())
                .ifPresent(user -> otpService.issue(user, OtpPurpose.PASSWORD_RESET, request.channel()));
    }

    /** For the GET form page: never consumes the link. */
    @Transactional(readOnly = true)
    public boolean isResetLinkValid(String token) {
        return otpService.checkLink(token, OtpPurpose.PASSWORD_RESET);
    }

    // noRollbackFor: a wrong SMS code must still use up one of the code's attempts.
    @Transactional(noRollbackFor = BadRequestException.class)
    public void resetPassword(ResetPasswordRequest request) {
        boolean byLink = request.token() != null;
        boolean byCode = request.email() != null || request.code() != null;
        if (byLink == byCode) {
            throw new BadRequestException("Send either token, or email and code");
        }
        passwordPolicy.validate(request.newPassword());
        OtpVerificationResult result = byLink
                ? otpService.verifyLink(request.token(), OtpPurpose.PASSWORD_RESET)
                : verifySmsCode(request.email(), request.code());
        if (!(result instanceof OtpVerificationResult.Valid(UUID userId))) {
            throw new BadRequestException(INVALID_CODE);
        }
        completeReset(userId, request.newPassword());
    }

    /** Form variant. Returns false when the link is not valid (the page shows it instead of a 400). */
    @Transactional
    public boolean resetPasswordWithLink(String token, String newPassword) {
        passwordPolicy.validate(newPassword);
        if (!(otpService.verifyLink(token, OtpPurpose.PASSWORD_RESET) instanceof OtpVerificationResult.Valid(
                UUID userId
        ))) {
            return false;
        }
        completeReset(userId, newPassword);
        return true;
    }

    @Transactional
    public void adminResetPassword(UUID userId, String newPassword) {
        passwordPolicy.validate(newPassword);
        setPassword(findUser(userId), newPassword);
    }

    private OtpVerificationResult verifySmsCode(String email, String code) {
        if (email == null || code == null) {
            return OtpVerificationResult.INVALID;
        }
        return userRepository.findByEmail(UserEntity.normalizeEmail(email))
                .map(user -> otpService.verifyCode(user.getId(), OtpPurpose.PASSWORD_RESET, code))
                .orElse(OtpVerificationResult.INVALID);
    }

    /** The other channel's reset code dies too: one successful reset closes the whole request. */
    private void completeReset(UUID userId, String newPassword) {
        setPassword(findUser(userId), newPassword);
        otpService.deleteAll(userId, OtpPurpose.PASSWORD_RESET);
    }

    private void setPassword(UserEntity user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
        refreshTokenStore.revokeAllForUser(user.getId().toString());
    }

    private UserEntity findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
