package com.gucardev.logtobasicsecurity.security;

import com.gucardev.logtobasicsecurity.user.AppUserService;
import com.gucardev.logtobasicsecurity.user.LocalUser;
import com.gucardev.logtobasicsecurity.user.UserProfile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/** Who is signed in: the principal from the security context, plus the application's own record of them. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CurrentUserService currentUser;
    private final AppUserService appUserService;

    /** For the signed-in user; the user comes from the security context, not from a parameter. */
    public UserProfile profile() {
        AuthUser user = currentUser.get();
        LocalUser localUser = appUserService.findByLogtoId(user.getId())
                .map(LocalUser::of)
                .orElse(null);
        List<String> authorities = user.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
        return new UserProfile(user.getId(), user.getEmail(), user.getDisplayName(), user.getRoles(), authorities,
                localUser, user.getClaims());
    }
}
