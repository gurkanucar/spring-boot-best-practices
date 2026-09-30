package com.gucardev.jwtauthrefreshtokenroles.user.controller;

import com.gucardev.jwtauthrefreshtokenroles.user.dto.AdminResetPasswordRequest;
import com.gucardev.jwtauthrefreshtokenroles.user.dto.ClaimValueRequest;
import com.gucardev.jwtauthrefreshtokenroles.user.dto.UserClaimResponse;
import com.gucardev.jwtauthrefreshtokenroles.user.service.AdminUserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Also guarded by /api/admin/** -> hasRole('ADMIN') in SecurityConfig; the annotation keeps it explicit here. */
@RestController
@RequestMapping("/api/admin/users/{id}")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/roles/{role}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addRole(@PathVariable UUID id, @PathVariable String role) {
        adminUserService.addRole(id, role);
    }

    @DeleteMapping("/roles/{role}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRole(@PathVariable UUID id, @PathVariable String role) {
        adminUserService.removeRole(id, role);
    }

    @GetMapping("/claims")
    public List<UserClaimResponse> claims(@PathVariable UUID id) {
        return adminUserService.claims(id);
    }

    @PutMapping("/claims/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void putClaim(@PathVariable UUID id, @PathVariable String name,
                         @Valid @RequestBody ClaimValueRequest request) {
        adminUserService.putClaim(id, name, request.value());
    }

    @DeleteMapping("/claims/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeClaim(@PathVariable UUID id, @PathVariable String name) {
        adminUserService.removeClaim(id, name);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody AdminResetPasswordRequest request) {
        adminUserService.resetPassword(id, request.newPassword());
    }
}
