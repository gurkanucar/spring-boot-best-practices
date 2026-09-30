package com.gucardev.jwtauthrefreshtokenroles.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleName;
import com.gucardev.jwtauthrefreshtokenroles.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthFlowTest extends IntegrationTestSupport {

    @Test
    void loginReturnsBearerTokenPair() throws Exception {
        postJson("/api/auth/login", json("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    void accessTokenCarriesIdentityRolesAndVerificationClaims() throws Exception {
        Jwt jwt = jwtDecoder.decode(login(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken());

        assertThat(jwt.getClaimAsString("email")).isEqualTo(ADMIN_EMAIL);
        // Only assigned roles go into the token; the hierarchy is applied when authorizing.
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SUPERADMIN");
        assertThat(jwt.getClaimAsBoolean("email_verified")).isTrue();
        assertThat(jwt.getHeaders()).containsEntry("kid", "demo-key-1");
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() throws Exception {
        login("Admin@Example.COM", ADMIN_PASSWORD);
    }

    @Test
    void wrongPasswordAndUnknownEmailAreBoth401() throws Exception {
        postJson("/api/auth/login", json("email", ADMIN_EMAIL, "password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid e-mail or password"));
        postJson("/api/auth/login", json("email", uniqueEmail(), "password", "whatever1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid e-mail or password"));
    }

    @Test
    void unverifiedAccountWithCorrectPasswordIs403() throws Exception {
        String email = uniqueEmail();
        createUser(email, PASSWORD, false, RoleName.USER);

        postJson("/api/auth/login", json("email", email, "password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("E-mail address is not verified"));
    }

    @Test
    void unverifiedAccountWithWrongPasswordIsPlain401() throws Exception {
        String email = uniqueEmail();
        createUser(email, PASSWORD, false, RoleName.USER);

        postJson("/api/auth/login", json("email", email, "password", "wrong-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidLoginBodyIs400() throws Exception {
        postJson("/api/auth/login", json("email", "not-an-email", "password", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void refreshRotatesTheToken() throws Exception {
        Tokens first = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        Tokens second = Tokens.from(refresh(first.refreshToken()).andExpect(status().isOk()).andReturn());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        refresh(first.refreshToken()).andExpect(status().isUnauthorized());
        refresh(second.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void unknownRefreshTokenIs401() throws Exception {
        refresh("never-issued").andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesRefreshTokenAndIsIdempotent() throws Exception {
        Tokens tokens = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        postJson("/api/auth/logout", json("refreshToken", tokens.refreshToken())).andExpect(status().isNoContent());
        postJson("/api/auth/logout", json("refreshToken", tokens.refreshToken())).andExpect(status().isNoContent());
        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
    }
}
