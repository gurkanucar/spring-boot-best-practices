package com.gucardev.jwtauthrefreshtokenroles.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import com.gucardev.jwtauthrefreshtokenroles.support.IntegrationTestSupport;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Locale;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;

class RegistrationFlowTest extends IntegrationTestSupport {

    private void register(String email, String phone) throws Exception {
        postJson("/api/auth/register", json("email", email, "password", PASSWORD, "phone", phone))
                .andExpect(status().isCreated());
    }

    private String verificationToken(String email) {
        var mail = lastEmail(email);
        assertThat(mail.purpose()).isEqualTo(OtpPurpose.EMAIL_VERIFICATION);
        return mail.code();
    }

    @Test
    void registerThenVerifyThenLogin() throws Exception {
        String email = uniqueEmail();
        postJson("/api/auth/register", json("email", email, "password", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        postJson("/api/auth/login", json("email", email, "password", PASSWORD)).andExpect(status().isForbidden());

        postJson("/api/auth/verify-email", json("token", verificationToken(email))).andExpect(status().isNoContent());

        login(email, PASSWORD);
    }

    @Test
    void verificationTokenWorksOnce() throws Exception {
        String email = uniqueEmail();
        register(email, null);
        String token = verificationToken(email);

        postJson("/api/auth/verify-email", json("token", token)).andExpect(status().isNoContent());
        postJson("/api/auth/verify-email", json("token", token)).andExpect(status().isBadRequest());
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        // Locale.ROOT on purpose: with a Turkish default locale "i".toUpperCase() is "İ".
        String local = "Mixed.Case-" + System.nanoTime();
        String lower = local.toLowerCase(Locale.ROOT);
        String upper = local.toUpperCase(Locale.ROOT);
        postJson("/api/auth/register", json("email", local + "@Example.com", "password", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(lower + "@example.com"));

        postJson("/api/auth/register", json("email", upper + "@EXAMPLE.COM", "password", PASSWORD))
                .andExpect(status().isConflict());

        postJson("/api/auth/verify-email", json("token", verificationToken(lower + "@example.com")))
                .andExpect(status().isNoContent());
        login(upper + "@example.com", PASSWORD);
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        String email = uniqueEmail();
        register(email, null);

        postJson("/api/auth/register", json("email", email, "password", PASSWORD)).andExpect(status().isConflict());
    }

    @Test
    void registeringWithAnotherUsersVerifiedNumberDoesNotRevealIt() throws Exception {
        // The number only becomes the user's after the SMS code is confirmed; that step answers 409.
        String phone = uniquePhone();
        createUserWithVerifiedPhone(uniqueEmail(), phone);
        String email = uniqueEmail();

        register(email, phone);

        UserEntity user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getPhone()).isNull();
        assertThat(user.getPendingPhone()).isEqualTo(phone);
    }

    @Test
    void invalidInputIs400() throws Exception {
        postJson("/api/auth/register", json("email", uniqueEmail(), "password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Password must be at least 8 characters"));
        postJson("/api/auth/register", json("email", uniqueEmail(), "password", PASSWORD, "phone", "05551112233"))
                .andExpect(status().isBadRequest());
        postJson("/api/auth/register", json("email", "nope", "password", PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerWithPhoneSendsSmsCode() throws Exception {
        String phone = uniquePhone();
        String email = uniqueEmail();
        register(email, phone);

        var sms = lastSms(phone);
        assertThat(sms.purpose()).isEqualTo(OtpPurpose.PHONE_VERIFICATION);
        assertThat(sms.code()).matches("\\d{6}");
        UserEntity user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getPhone()).isNull();
        assertThat(user.getPendingPhone()).isEqualTo(phone);
        assertThat(user.isPhoneVerified()).isFalse();
    }

    @Test
    void registerSucceedsWhenMailServerIsDown() throws Exception {
        doThrow(new MailSendException("SMTP down")).when(javaMailSender).send(any(MimeMessage.class));

        postJson("/api/auth/register", json("email", uniqueEmail(), "password", PASSWORD))
                .andExpect(status().isCreated());
    }

    @Test
    void confirmationPageDoesNotVerifyButItsFormDoes() throws Exception {
        String email = uniqueEmail();
        register(email, null);
        String token = verificationToken(email);

        mockMvc.perform(get("/api/auth/verify-email").param("token", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().string(Matchers.containsString("Confirm my e-mail")));
        mockMvc.perform(get("/api/auth/verify-email").param("token", token)).andExpect(status().isOk());
        postJson("/api/auth/login", json("email", email, "password", PASSWORD)).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/verify-email/submit")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("E-mail confirmed")));

        login(email, PASSWORD);
    }

    @Test
    void confirmationPageForUnknownTokenSaysLinkIsInvalid() throws Exception {
        mockMvc.perform(get("/api/auth/verify-email").param("token", "bogus"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Link is no longer valid")));
    }

    @Test
    void pagesLoadNoExternalResources() throws Exception {
        String email = uniqueEmail();
        register(email, null);

        String html = mockMvc.perform(get("/api/auth/verify-email").param("token", verificationToken(email)))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("<script", "<link", "src=");
    }

    @Test
    void resendRespectsCooldownAndOnlyTargetsUnverifiedUsers() throws Exception {
        String email = uniqueEmail();
        register(email, null);

        postJson("/api/auth/resend-verification", json("email", email)).andExpect(status().isAccepted());
        assertThat(sentEmails(email)).hasSize(1);

        UserEntity user = userRepository.findByEmail(email).orElseThrow();
        ageCodes(user.getId(), Duration.ofSeconds(61));
        postJson("/api/auth/resend-verification", json("email", email)).andExpect(status().isAccepted());
        assertThat(sentEmails(email)).hasSize(2);

        postJson("/api/auth/resend-verification", json("email", uniqueEmail())).andExpect(status().isAccepted());
        postJson("/api/auth/resend-verification", json("email", ADMIN_EMAIL)).andExpect(status().isAccepted());
        assertThat(sentEmails(ADMIN_EMAIL)).isEmpty();
    }
}
