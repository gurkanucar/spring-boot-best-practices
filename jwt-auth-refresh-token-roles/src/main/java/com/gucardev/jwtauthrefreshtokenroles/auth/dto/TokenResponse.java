package com.gucardev.jwtauthrefreshtokenroles.auth.dto;

import com.gucardev.jwtauthrefreshtokenroles.token.access.IssuedAccessToken;

/** expiresIn is the access token lifetime in seconds, as in OAuth2 token responses. */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

    public static TokenResponse bearer(IssuedAccessToken accessToken, String refreshToken) {
        return new TokenResponse(accessToken.value(), refreshToken, "Bearer", accessToken.expiresIn().toSeconds());
    }
}
