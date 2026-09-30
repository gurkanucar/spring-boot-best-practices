package com.gucardev.jwtauthrefreshtokenroles.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleName;
import com.gucardev.jwtauthrefreshtokenroles.support.IntegrationTestSupport;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PasswordFlowTest extends IntegrationTestSupport {

    private static final String NEW_PASSWORD = "brand-new-password";

    private String verifiedUser() {
        String email = uniqueEmail();
        createUser(email, PASSWORD, true, RoleName.USER);
        return email;
    }

    private void forgot(String email, String channel) throws Exception {
        postJson("/api/auth/forgot-password", json("email", email, "channel", channel))
                .andExpect(status().isAccepted());
    }

    private static String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void changePasswordRevokesRefreshTokens() throws Exception {
        String email = verifiedUser();
        Tokens tokens = login(email, PASSWORD);

        postJson("/api/auth/change-password",
                json("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD), tokens.accessToken())
                .andExpect(status().isNoContent());

        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", json("email", email, "password", PASSWORD)).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD);
    }

    @Test
    void changePasswordRejectsWrongCurrentOrWeakNewPassword() throws Exception {
        String email = verifiedUser();
        String accessToken = login(email, PASSWORD).accessToken();

        postJson("/api/auth/change-password",
                json("currentPassword", "wrong-password", "newPassword", NEW_PASSWORD), accessToken)
                .andExpect(status().isBadRequest());
        postJson("/api/auth/change-password",
                json("currentPassword", PASSWORD, "newPassword", "short"), accessToken)
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordNeedsAuthentication() throws Exception {
        postJson("/api/auth/change-password", json("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordIsSilentForUnknownEmail() throws Exception {
        String email = uniqueEmail();
        forgot(email, "EMAIL");
        assertThat(sentEmails(email)).isEmpty();
    }

    @Test
    void resetWithEmailLinkWorksOnceAndRevokesSessions() throws Exception {
        String email = verifiedUser();
        Tokens tokens = login(email, PASSWORD);
        forgot(email, "EMAIL");
        String token = lastEmail(email).code();

        postJson("/api/auth/reset-password", json("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());

        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD);
        postJson("/api/auth/reset-password", json("token", token, "newPassword", "another-password"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetNeedsExactlyOneVariant() throws Exception {
        postJson("/api/auth/reset-password",
                json("token", "t", "email", uniqueEmail(), "code", "123456", "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
        postJson("/api/auth/reset-password", json("newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetFormPageDoesNotConsumeTheLink() throws Exception {
        String email = verifiedUser();
        forgot(email, "EMAIL");
        String token = lastEmail(email).code();

        mockMvc.perform(get("/api/auth/reset-password").param("token", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().string(Matchers.containsString("name=\"newPassword\"")));
        mockMvc.perform(get("/api/auth/reset-password").param("token", token))
                .andExpect(content().string(Matchers.containsString("name=\"newPassword\"")));

        mockMvc.perform(post("/api/auth/reset-password/submit").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token).param("newPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Password must be at least 8 characters")));

        mockMvc.perform(post("/api/auth/reset-password/submit").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token).param("newPassword", NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Password updated")));

        login(email, NEW_PASSWORD);
        mockMvc.perform(get("/api/auth/reset-password").param("token", token))
                .andExpect(content().string(Matchers.containsString("Link is no longer valid")));
    }

    @Test
    void resetWithSmsCode() throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        createUserWithVerifiedPhone(email, phone);
        forgot(email, "SMS");
        String code = lastSms(phone).code();

        postJson("/api/auth/reset-password", json("email", email, "code", code, "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());

        login(email, NEW_PASSWORD);
    }

    @Test
    void smsResetIsNotSentToUnverifiedPhone() throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        createUser(email, PASSWORD, true, RoleName.USER);
        transactionTemplate.executeWithoutResult(status ->
                userRepository.findByEmail(email).orElseThrow().setPendingPhone(phone));

        forgot(email, "SMS");

        assertThat(sentSms(phone)).isEmpty();
    }

    @Test
    void fiveWrongSmsCodesBurnTheCode() throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        createUserWithVerifiedPhone(email, phone);
        forgot(email, "SMS");
        String code = lastSms(phone).code();

        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/reset-password",
                    json("email", email, "code", wrongCode(code), "newPassword", NEW_PASSWORD))
                    .andExpect(status().isBadRequest());
        }

        postJson("/api/auth/reset-password", json("email", email, "code", code, "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void channelsAreIndependentUntilOneResetSucceeds() throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        createUserWithVerifiedPhone(email, phone);
        forgot(email, "EMAIL");
        forgot(email, "SMS");
        String link = lastEmail(email).code();
        String code = lastSms(phone).code();

        mockMvc.perform(get("/api/auth/reset-password").param("token", link))
                .andExpect(content().string(Matchers.containsString("name=\"newPassword\"")));

        postJson("/api/auth/reset-password", json("email", email, "code", code, "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());

        postJson("/api/auth/reset-password", json("token", link, "newPassword", "another-password"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void secondForgotWithinCooldownSendsNothing() throws Exception {
        String email = verifiedUser();
        forgot(email, "EMAIL");
        forgot(email, "EMAIL");

        assertThat(sentEmails(email)).hasSize(1);
    }
}
