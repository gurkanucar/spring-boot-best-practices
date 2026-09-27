package com.gucardev.logtobasicsecurity.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * The signed-in user as a controller parameter, {@code null} when nobody is signed in:
 *
 * <pre>{@code
 * public Me me(@CurrentUser OidcUser user) { ... }
 * }</pre>
 *
 * <p>A shorter name for {@link AuthenticationPrincipal}, and one place to change if the principal
 * type ever changes. Only for controllers; services use {@link CurrentUserService}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal
public @interface CurrentUser {
}
