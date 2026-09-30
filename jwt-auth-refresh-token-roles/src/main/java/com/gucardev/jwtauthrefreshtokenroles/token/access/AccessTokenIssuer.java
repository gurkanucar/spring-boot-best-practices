package com.gucardev.jwtauthrefreshtokenroles.token.access;

import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;

/** Builds and signs the access token for a user. Must be called inside a transaction (reads roles/claims). */
public interface AccessTokenIssuer {

    IssuedAccessToken issue(UserEntity user);
}
