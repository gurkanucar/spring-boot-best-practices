package com.gucardev.casdoorbasicsecurity.user;

import java.util.List;
import java.util.Map;

/**
 * @param roles role names as Casdoor has them
 * @param localUser the application's own row, null before it exists
 * @param claims everything Casdoor sent: ID token claims merged with the userinfo response
 */
public record UserProfile(String id, String email, String name, List<String> roles, List<String> authorities,
                 LocalUser localUser, Map<String, Object> claims) {
}
