package com.gucardev.jwtauthrefreshtokenroles.user.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything the access token says about the caller, custom claims included. */
public record MeResponse(UUID id, String email, List<String> roles, Map<String, Object> claims) {
}
