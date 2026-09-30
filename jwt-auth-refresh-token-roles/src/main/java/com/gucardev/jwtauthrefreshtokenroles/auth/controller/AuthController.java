package com.gucardev.jwtauthrefreshtokenroles.auth.controller;

import com.gucardev.jwtauthrefreshtokenroles.auth.dto.ChangePasswordRequest;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.ForgotPasswordRequest;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.LoginRequest;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.LogoutRequest;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.RefreshRequest;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.RegisterRequest;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.RegisterResponse;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.ResendVerificationRequest;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.ResetPasswordRequest;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.TokenResponse;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.VerifyEmailRequest;
import com.gucardev.jwtauthrefreshtokenroles.auth.service.AuthService;
import com.gucardev.jwtauthrefreshtokenroles.auth.service.EmailVerificationService;
import com.gucardev.jwtauthrefreshtokenroles.auth.service.PasswordService;
import com.gucardev.jwtauthrefreshtokenroles.auth.service.RegistrationService;
import com.gucardev.jwtauthrefreshtokenroles.common.error.BadRequestException;
import com.gucardev.jwtauthrefreshtokenroles.token.access.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordService passwordService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    /** JSON variant for SPAs; the browser variant is GET page + POST /verify-email/submit. */
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        if (!emailVerificationService.verify(request.token())) {
            throw new BadRequestException("Invalid or expired link");
        }
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.email());
    }

    /** Requires a valid access token: not listed in security.public-paths. */
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        passwordService.changePassword(CurrentUser.from(jwt).id(), request);
    }

    /** Always 202 with the same body, whether or not the account exists. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.resetPassword(request);
    }
}
