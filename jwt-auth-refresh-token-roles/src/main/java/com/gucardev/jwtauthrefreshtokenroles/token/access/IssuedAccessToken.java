package com.gucardev.jwtauthrefreshtokenroles.token.access;

import java.time.Duration;

public record IssuedAccessToken(String value, Duration expiresIn) {
}
