package com.gucardev.jwtauthrefreshtokenroles.token.access.claims;

import java.util.Set;

/** Claims the issuer itself owns; no contributor or stored user claim may use these names. */
public final class StandardClaims {

    public static final String EMAIL = "email";
    public static final String ROLES = "roles";

    public static final Set<String> NAMES =
            Set.of("iss", "sub", "aud", "exp", "nbf", "iat", "jti", "scope", EMAIL, ROLES);

    private StandardClaims() {
    }
}
