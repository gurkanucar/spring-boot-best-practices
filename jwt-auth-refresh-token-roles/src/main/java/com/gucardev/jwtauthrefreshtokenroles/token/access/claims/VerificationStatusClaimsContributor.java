package com.gucardev.jwtauthrefreshtokenroles.token.access.claims;

import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Example contributor from code: tells resource servers whether e-mail and phone are verified. */
@Component
public class VerificationStatusClaimsContributor implements JwtClaimsContributor {

    public static final String EMAIL_VERIFIED = "email_verified";
    public static final String PHONE_VERIFIED = "phone_verified";

    @Override
    public Set<String> declaredClaims() {
        return Set.of(EMAIL_VERIFIED, PHONE_VERIFIED);
    }

    @Override
    public Map<String, Object> contribute(UserEntity user) {
        return Map.of(EMAIL_VERIFIED, user.isEmailVerified(), PHONE_VERIFIED, user.isPhoneVerified());
    }
}
