package com.gucardev.jwtauthrefreshtokenroles.user.controller;

import com.gucardev.jwtauthrefreshtokenroles.token.access.CurrentUser;
import com.gucardev.jwtauthrefreshtokenroles.user.dto.MeResponse;
import com.gucardev.jwtauthrefreshtokenroles.user.dto.PhoneRequest;
import com.gucardev.jwtauthrefreshtokenroles.user.dto.VerifyPhoneRequest;
import com.gucardev.jwtauthrefreshtokenroles.user.service.MeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return meService.me(jwt);
    }

    /** Saves the number as unverified and texts a code to it. */
    @PutMapping("/phone")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void changePhone(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PhoneRequest request) {
        meService.changePhone(CurrentUser.from(jwt).id(), request.phone());
    }

    @PostMapping("/phone/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyPhone(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VerifyPhoneRequest request) {
        meService.verifyPhone(CurrentUser.from(jwt).id(), request.code());
    }
}
