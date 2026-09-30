package com.gucardev.jwtauthrefreshtokenroles.token.access.claims;

import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserClaimEntity;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Adds the per-user claims admins store in the database. Names are validated when they are stored. */
@Component
public class UserClaimsContributor implements JwtClaimsContributor {

    @Override
    public Set<String> declaredClaims() {
        return Set.of();
    }

    @Override
    public Map<String, Object> contribute(UserEntity user) {
        return user.getClaims().stream()
                .collect(Collectors.toMap(UserClaimEntity::getName, claim -> (Object) claim.getValue()));
    }
}
