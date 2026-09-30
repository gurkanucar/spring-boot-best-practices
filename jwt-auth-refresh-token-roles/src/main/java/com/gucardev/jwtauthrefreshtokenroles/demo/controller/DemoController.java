package com.gucardev.jwtauthrefreshtokenroles.demo.controller;

import com.gucardev.jwtauthrefreshtokenroles.demo.dto.MessageResponse;
import com.gucardev.jwtauthrefreshtokenroles.demo.service.DemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DemoController {

    private final DemoService demoService;

    /** Open to everyone, because /api/public/** is listed in security.public-paths. */
    @GetMapping("/api/public/hello")
    public MessageResponse publicHello() {
        return demoService.publicContent();
    }

    /** Any authenticated user, no role requirement. */
    @GetMapping("/api/demo/authenticated")
    public MessageResponse authenticatedHello(Authentication auth) {
        return demoService.authenticatedContent(auth);
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/api/demo/user")
    public MessageResponse userHello(Authentication auth) {
        return demoService.userContent(auth);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/demo/admin")
    public MessageResponse adminHello(Authentication auth) {
        return demoService.adminContent(auth);
    }

    /** Only the superadmin; ADMIN does not imply SUPERADMIN. */
    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/api/demo/superadmin")
    public MessageResponse superadminHello(Authentication auth) {
        return demoService.superadminContent(auth);
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/api/demo/shared")
    public MessageResponse sharedHello(Authentication auth) {
        return demoService.sharedContent(auth);
    }
}
