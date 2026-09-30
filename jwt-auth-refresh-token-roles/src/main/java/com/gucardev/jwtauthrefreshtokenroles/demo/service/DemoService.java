package com.gucardev.jwtauthrefreshtokenroles.demo.service;

import com.gucardev.jwtauthrefreshtokenroles.demo.dto.MessageResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class DemoService {

    public MessageResponse publicContent() {
        return new MessageResponse("Hello, anonymous visitor", null, List.of());
    }

    public MessageResponse authenticatedContent(Authentication auth) {
        return describe("Hello, authenticated user", auth);
    }

    public MessageResponse userContent(Authentication auth) {
        return describe("Hello, USER", auth);
    }

    public MessageResponse adminContent(Authentication auth) {
        return describe("Hello, ADMIN", auth);
    }

    public MessageResponse superadminContent(Authentication auth) {
        return describe("Hello, SUPERADMIN", auth);
    }

    public MessageResponse sharedContent(Authentication auth) {
        return describe("Hello, USER or ADMIN", auth);
    }

    private static MessageResponse describe(String message, Authentication auth) {
        List<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        return new MessageResponse(message, auth.getName(), authorities);
    }
}
