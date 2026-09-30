package com.gucardev.jwtauthrefreshtokenroles.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleName;
import com.gucardev.jwtauthrefreshtokenroles.support.IntegrationTestSupport;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class MeAndAdminTest extends IntegrationTestSupport {

    private String adminToken;

    @BeforeEach
    void loginAdmin() throws Exception {
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken();
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer(adminToken)));
    }

    private ResultActions putPhone(String accessToken, String phone) throws Exception {
        return mockMvc.perform(put("/api/me/phone").contentType(MediaType.APPLICATION_JSON)
                .content(json("phone", phone)).header("Authorization", bearer(accessToken)));
    }

    private static String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void meShowsIdentityRolesAndClaims() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.roles[0]").value("SUPERADMIN"))
                .andExpect(jsonPath("$.claims.email_verified").value(true));
    }

    @Test
    void meNeedsAuthentication() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void phoneIsVerifiedWithSmsCode() throws Exception {
        String email = uniqueEmail();
        createUser(email, PASSWORD, true, RoleName.USER);
        String token = login(email, PASSWORD).accessToken();
        String phone = uniquePhone();

        putPhone(token, phone).andExpect(status().isAccepted());
        String code = lastSms(phone).code();

        postJson("/api/me/phone/verify", json("code", wrongCode(code)), token).andExpect(status().isBadRequest());
        postJson("/api/me/phone/verify", json("code", code), token).andExpect(status().isNoContent());

        assertThat(jwtDecoder.decode(login(email, PASSWORD).accessToken()).getClaimAsBoolean("phone_verified")).isTrue();
    }

    @Test
    void verifyingANumberAnotherUserVerifiedIs409() throws Exception {
        String phone = uniquePhone();
        createUserWithVerifiedPhone(uniqueEmail(), phone);
        String email = uniqueEmail();
        createUser(email, PASSWORD, true, RoleName.USER);
        String token = login(email, PASSWORD).accessToken();

        putPhone(token, phone).andExpect(status().isAccepted());
        postJson("/api/me/phone/verify", json("code", lastSms(phone).code()), token)
                .andExpect(status().isConflict());
    }

    @Test
    void unverifiedNumberDoesNotBlockItsRealOwner() throws Exception {
        String phone = uniquePhone();
        String squatter = uniqueEmail();
        createUser(squatter, PASSWORD, true, RoleName.USER);
        putPhone(login(squatter, PASSWORD).accessToken(), phone).andExpect(status().isAccepted());

        String owner = uniqueEmail();
        createUser(owner, PASSWORD, true, RoleName.USER);
        String token = login(owner, PASSWORD).accessToken();
        putPhone(token, phone).andExpect(status().isAccepted());
        postJson("/api/me/phone/verify", json("code", lastSms(phone).code()), token)
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByEmail(owner).orElseThrow().getPhone()).isEqualTo(phone);
    }

    @Test
    void puttingTheCurrentVerifiedNumberSendsNothing() throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        createUserWithVerifiedPhone(email, phone);

        putPhone(login(email, PASSWORD).accessToken(), phone).andExpect(status().isAccepted());

        assertThat(sentSms(phone)).isEmpty();
        assertThat(jwtDecoder.decode(login(email, PASSWORD).accessToken()).getClaimAsBoolean("phone_verified")).isTrue();
    }

    @Test
    void oldNumberStaysVerifiedUntilTheNewOneIs() throws Exception {
        String email = uniqueEmail();
        String oldPhone = uniquePhone();
        String newPhone = uniquePhone();
        createUserWithVerifiedPhone(email, oldPhone);

        putPhone(login(email, PASSWORD).accessToken(), newPhone).andExpect(status().isAccepted());

        assertThat(jwtDecoder.decode(login(email, PASSWORD).accessToken()).getClaimAsBoolean("phone_verified")).isTrue();
        postJson("/api/auth/forgot-password", json("email", email, "channel", "SMS")).andExpect(status().isAccepted());
        assertThat(lastSms(oldPhone).purpose().name()).isEqualTo("PASSWORD_RESET");
    }

    @Test
    void changingPhoneTwiceQuicklyStillSendsCodeToNewNumber() throws Exception {
        String email = uniqueEmail();
        createUser(email, PASSWORD, true, RoleName.USER);
        String token = login(email, PASSWORD).accessToken();
        String first = uniquePhone();
        String second = uniquePhone();

        putPhone(token, first).andExpect(status().isAccepted());
        String firstCode = lastSms(first).code();
        putPhone(token, second).andExpect(status().isAccepted());
        String secondCode = lastSms(second).code();

        postJson("/api/me/phone/verify", json("code", firstCode), token).andExpect(status().isBadRequest());
        postJson("/api/me/phone/verify", json("code", secondCode), token).andExpect(status().isNoContent());
    }

    @Test
    void adminAddsRoleAndUserMustLogInAgain() throws Exception {
        String email = uniqueEmail();
        UserEntity user = createUser(email, PASSWORD, true, RoleName.USER);
        Tokens tokens = login(email, PASSWORD);

        asAdmin(post("/api/admin/users/{id}/roles/{role}", user.getId(), "ADMIN")).andExpect(status().isNoContent());

        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
        String newToken = login(email, PASSWORD).accessToken();
        mockMvc.perform(get("/api/demo/admin").header("Authorization", bearer(newToken))).andExpect(status().isOk());

        asAdmin(delete("/api/admin/users/{id}/roles/{role}", user.getId(), "ADMIN")).andExpect(status().isNoContent());
        String demoted = login(email, PASSWORD).accessToken();
        mockMvc.perform(get("/api/demo/admin").header("Authorization", bearer(demoted))).andExpect(status().isForbidden());
    }

    @Test
    void roleErrors() throws Exception {
        UserEntity user = createUser(uniqueEmail(), PASSWORD, true, RoleName.USER);

        asAdmin(post("/api/admin/users/{id}/roles/{role}", user.getId(), "GOD")).andExpect(status().isNotFound());
        asAdmin(delete("/api/admin/users/{id}/roles/{role}", user.getId(), "ADMIN")).andExpect(status().isNotFound());
        asAdmin(post("/api/admin/users/{id}/roles/{role}", UUID.randomUUID(), "USER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void superadminRoleCannotBeAssignedOrRemoved() throws Exception {
        UserEntity user = createUser(uniqueEmail(), PASSWORD, true, RoleName.USER);
        UUID superadminId = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();

        asAdmin(post("/api/admin/users/{id}/roles/{role}", user.getId(), "SUPERADMIN"))
                .andExpect(status().isBadRequest());
        asAdmin(delete("/api/admin/users/{id}/roles/{role}", superadminId, "SUPERADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCannotManageTheSuperadminAccount() throws Exception {
        String email = uniqueEmail();
        createUser(email, PASSWORD, true, RoleName.ADMIN);
        String adminToken = login(email, PASSWORD).accessToken();
        UUID superadminId = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();

        mockMvc.perform(post("/api/admin/users/{id}/reset-password", superadminId)
                        .contentType(MediaType.APPLICATION_JSON).content(json("newPassword", "taken-over-1"))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/users/{id}/claims/{name}", superadminId, "note")
                        .contentType(MediaType.APPLICATION_JSON).content(json("value", "x"))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users/{id}/roles/{role}", superadminId, "USER")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users/{id}/claims", superadminId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());

        login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    @Test
    void adminRoleCanUseTheAdminApiOnOrdinaryUsers() throws Exception {
        String email = uniqueEmail();
        createUser(email, PASSWORD, true, RoleName.ADMIN);
        String adminToken = login(email, PASSWORD).accessToken();
        UserEntity user = createUser(uniqueEmail(), PASSWORD, true, RoleName.USER);

        mockMvc.perform(put("/api/admin/users/{id}/claims/{name}", user.getId(), "tenant_id")
                        .contentType(MediaType.APPLICATION_JSON).content(json("value", "acme"))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void superadminCanManageItsOwnAccount() throws Exception {
        UUID superadminId = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();

        asAdmin(put("/api/admin/users/{id}/claims/{name}", superadminId, "note")
                .contentType(MediaType.APPLICATION_JSON).content(json("value", "x")))
                .andExpect(status().isNoContent());
        asAdmin(delete("/api/admin/users/{id}/claims/{name}", superadminId, "note"))
                .andExpect(status().isNoContent());
    }

    @Test
    void userCannotCallAdminApi() throws Exception {
        String email = uniqueEmail();
        UserEntity user = createUser(email, PASSWORD, true, RoleName.USER);
        String token = login(email, PASSWORD).accessToken();

        mockMvc.perform(get("/api/admin/users/{id}/claims", user.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminClaimAppearsInNextTokenAndMe() throws Exception {
        String email = uniqueEmail();
        UserEntity user = createUser(email, PASSWORD, true, RoleName.USER);
        Tokens tokens = login(email, PASSWORD);

        asAdmin(put("/api/admin/users/{id}/claims/{name}", user.getId(), "tenant_id")
                .contentType(MediaType.APPLICATION_JSON).content(json("value", "acme")))
                .andExpect(status().isNoContent());

        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
        String token = login(email, PASSWORD).accessToken();
        assertThat(jwtDecoder.decode(token).getClaimAsString("tenant_id")).isEqualTo("acme");
        mockMvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.claims.tenant_id").value("acme"));
        asAdmin(get("/api/admin/users/{id}/claims", user.getId()))
                .andExpect(jsonPath("$[0].name").value("tenant_id"))
                .andExpect(jsonPath("$[0].value").value("acme"));

        asAdmin(delete("/api/admin/users/{id}/claims/{name}", user.getId(), "tenant_id")).andExpect(status().isNoContent());
        asAdmin(delete("/api/admin/users/{id}/claims/{name}", user.getId(), "tenant_id")).andExpect(status().isNotFound());
        assertThat(jwtDecoder.decode(login(email, PASSWORD).accessToken()).hasClaim("tenant_id")).isFalse();
    }

    @Test
    void reservedOrInvalidClaimNamesAre400() throws Exception {
        UserEntity user = createUser(uniqueEmail(), PASSWORD, true, RoleName.USER);

        for (String name : new String[] {"sub", "roles", "email_verified", "Tenant-Id"}) {
            asAdmin(put("/api/admin/users/{id}/claims/{name}", user.getId(), name)
                    .contentType(MediaType.APPLICATION_JSON).content(json("value", "x")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void adminResetPasswordRevokesSessions() throws Exception {
        String email = uniqueEmail();
        UserEntity user = createUser(email, PASSWORD, true, RoleName.USER);
        Tokens tokens = login(email, PASSWORD);

        asAdmin(post("/api/admin/users/{id}/reset-password", user.getId())
                .contentType(MediaType.APPLICATION_JSON).content(json("newPassword", "admin-chosen-1")))
                .andExpect(status().isNoContent());

        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
        login(email, "admin-chosen-1");
    }
}
