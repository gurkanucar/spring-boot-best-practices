package com.gucardev.jwtauthrefreshtokenroles.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleName;
import com.gucardev.jwtauthrefreshtokenroles.support.IntegrationTestSupport;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class AccessControlTest extends IntegrationTestSupport {

    private String userToken() throws Exception {
        String email = uniqueEmail();
        createUser(email, PASSWORD, true, RoleName.USER);
        return login(email, PASSWORD).accessToken();
    }

    @Test
    void publicEndpointNeedsNoToken() throws Exception {
        mockMvc.perform(get("/api/public/hello")).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/demo/authenticated")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIs401() throws Exception {
        mockMvc.perform(get("/api/demo/authenticated").header("Authorization", bearer("not.a.jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userRoleCanReachUserAndSharedButNotAdmin() throws Exception {
        String token = userToken();

        mockMvc.perform(get("/api/demo/authenticated").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        // Spring Security 7 also adds a FACTOR_BEARER authority to every JWT authentication.
        mockMvc.perform(get("/api/demo/user").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorities", Matchers.hasItem("ROLE_USER")))
                .andExpect(jsonPath("$.authorities", Matchers.not(Matchers.hasItem("ROLE_ADMIN"))));
        mockMvc.perform(get("/api/demo/shared").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/demo/admin").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoleImpliesUserButNotSuperadmin() throws Exception {
        String email = uniqueEmail();
        createUser(email, PASSWORD, true, RoleName.ADMIN);
        String token = login(email, PASSWORD).accessToken();

        for (String path : new String[] {"/api/demo/admin", "/api/demo/user", "/api/demo/shared"}) {
            mockMvc.perform(get(path).header("Authorization", bearer(token))).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/demo/superadmin").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void superadminImpliesAdminAndUser() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken();

        for (String path : new String[] {"/api/demo/superadmin", "/api/demo/admin", "/api/demo/user", "/api/demo/shared"}) {
            mockMvc.perform(get(path).header("Authorization", bearer(token))).andExpect(status().isOk());
        }
    }

    @Test
    void userCannotReachSuperadmin() throws Exception {
        mockMvc.perform(get("/api/demo/superadmin").header("Authorization", bearer(userToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void responsesCarryNoReferrerPolicy() throws Exception {
        mockMvc.perform(get("/api/public/hello"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }
}
