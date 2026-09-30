package com.gucardev.jwtauthrefreshtokenroles.token.access.claims;

import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import java.util.Map;
import java.util.Set;

/**
 * Adds custom claims to every access token. Implement it as a Spring bean to add a claim from code.
 * A contributor never sees the shared claim map: it returns its own claims, and the issuer rejects
 * any name that is already present, so bean order can never decide which value wins.
 */
public interface JwtClaimsContributor {

    /**
     * Claim names this contributor may emit. They become reserved: two contributors cannot declare
     * the same name, and admins cannot store a user claim with it. Return an empty set only for
     * contributors whose names are dynamic (e.g. taken from the database).
     */
    Set<String> declaredClaims();

    Map<String, Object> contribute(UserEntity user);
}
