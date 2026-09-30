package com.gucardev.jwtauthrefreshtokenroles.auth.service;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpService;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpVerificationResult;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import com.gucardev.jwtauthrefreshtokenroles.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final OtpService otpService;
    private final UserRepository userRepository;

    /** For the GET page. Mail scanners open links on their own, so this must never verify anything. */
    @Transactional(readOnly = true)
    public boolean isLinkValid(String token) {
        return otpService.checkLink(token, OtpPurpose.EMAIL_VERIFICATION);
    }

    @Transactional
    public boolean verify(String token) {
        if (!(otpService.verifyLink(token, OtpPurpose.EMAIL_VERIFICATION) instanceof OtpVerificationResult.Valid valid)) {
            return false;
        }
        userRepository.findById(valid.userId()).ifPresent(user -> user.setEmailVerified(true));
        return true;
    }

    /** Silent for unknown or already verified addresses: the response is the same either way. */
    @Transactional
    public void resend(String email) {
        userRepository.findByEmail(UserEntity.normalizeEmail(email))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> otpService.issue(user, OtpPurpose.EMAIL_VERIFICATION, OtpChannel.EMAIL));
    }
}
