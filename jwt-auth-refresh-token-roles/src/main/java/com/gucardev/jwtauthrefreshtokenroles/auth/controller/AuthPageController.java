package com.gucardev.jwtauthrefreshtokenroles.auth.controller;

import com.gucardev.jwtauthrefreshtokenroles.auth.service.EmailVerificationService;
import com.gucardev.jwtauthrefreshtokenroles.auth.service.PasswordService;
import com.gucardev.jwtauthrefreshtokenroles.common.security.PasswordPolicy;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The pages behind the links in the mails, so a mail can be completed straight from the browser
 * (handy with Mailpit). A SPA can ignore them and use the JSON endpoints in AuthController.
 * GET requests only show pages; every change happens in a POST, because mail scanners open links.
 */
@Controller
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthPageController {

    private final EmailVerificationService emailVerificationService;
    private final PasswordService passwordService;
    private final PasswordPolicy passwordPolicy;

    @GetMapping("/verify-email")
    public String verifyEmailPage(@RequestParam("token") String token, Model model) {
        if (!emailVerificationService.isLinkValid(token)) {
            return invalidLink(model, "confirmation");
        }
        model.addAttribute("token", token);
        return "pages/verify-email";
    }

    @PostMapping("/verify-email/submit")
    public String submitVerifyEmail(@RequestParam("token") String token, Model model) {
        if (!emailVerificationService.verify(token)) {
            return invalidLink(model, "confirmation");
        }
        return page(model, true, "E-mail confirmed", "Your account is active. You can sign in now.");
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam("token") String token, Model model) {
        if (!passwordService.isResetLinkValid(token)) {
            return invalidLink(model, "password reset");
        }
        model.addAttribute("token", token);
        return "pages/reset-password";
    }

    /** Target of the form above; the JSON variant is POST /api/auth/reset-password. */
    @PostMapping("/reset-password/submit")
    public String submitResetPassword(@RequestParam("token") String token,
                                      @RequestParam("newPassword") String newPassword,
                                      Model model) {
        Optional<String> violation = passwordPolicy.check(newPassword);
        if (violation.isPresent()) {
            model.addAttribute("token", token);
            model.addAttribute("error", violation.get());
            return "pages/reset-password";
        }
        if (!passwordService.resetPasswordWithLink(token, newPassword)) {
            return invalidLink(model, "password reset");
        }
        return page(model, true, "Password updated",
                "You can sign in with your new password. Sessions on other devices were signed out.");
    }

    private String invalidLink(Model model, String kind) {
        return page(model, false, "Link is no longer valid",
                "This " + kind + " link has expired or was already used. Request a new one.");
    }

    private String page(Model model, boolean success, String title, String message) {
        model.addAttribute("success", success);
        model.addAttribute("title", title);
        model.addAttribute("message", message);
        return "pages/result";
    }
}
