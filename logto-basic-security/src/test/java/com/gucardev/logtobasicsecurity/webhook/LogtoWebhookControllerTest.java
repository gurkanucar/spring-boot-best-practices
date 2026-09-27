package com.gucardev.logtobasicsecurity.webhook;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.logtobasicsecurity.security.LogtoOidcUserService;
import com.gucardev.logtobasicsecurity.security.SecurityConfig;
import com.gucardev.logtobasicsecurity.security.TestLogtoRegistration;
import com.gucardev.logtobasicsecurity.user.AppUserService;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Payloads shaped like Logto's (only the fields used, plus a few it also sends). */
@WebMvcTest(controllers = LogtoWebhookController.class, properties = "app.logto.webhook-signing-key=" + LogtoWebhookControllerTest.KEY)
@Import({SecurityConfig.class, LogtoOidcUserService.class, LogtoWebhookSignature.class, LogtoUserSync.class,
        TestLogtoRegistration.class})
class LogtoWebhookControllerTest {

    static final String KEY = "test-signing-key";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserService appUserService;

    @Test
    void signUpCreatesTheLocalUser() throws Exception {
        send("""
                {"hookId":"h1","event":"PostRegister","createdAt":"2026-09-27T20:00:00.000Z",
                 "interactionEvent":"Register","userId":"u1",
                 "user":{"id":"u1","username":"alice","primaryEmail":"alice@example.com","name":"Alice","customData":{}}}
                """).andExpect(status().isNoContent());

        verify(appUserService).syncFromLogto("u1", "alice@example.com", "Alice", "alice");
    }

    @Test
    void userCreatedOrUpdatedInLogtoIsSynced() throws Exception {
        send("""
                {"hookId":"h1","event":"User.Created","createdAt":"2026-09-27T20:00:00.000Z",
                 "path":"/users","method":"POST","status":200,
                 "data":{"id":"u2","username":"bob","primaryEmail":null,"name":null}}
                """).andExpect(status().isNoContent());
        send("""
                {"hookId":"h1","event":"User.Data.Updated","createdAt":"2026-09-27T20:01:00.000Z",
                 "path":"/users/u2","method":"PATCH","status":200,"params":{"userId":"u2"},
                 "data":{"id":"u2","username":"bob","primaryEmail":"bob@example.com","name":"Bob"}}
                """).andExpect(status().isNoContent());

        verify(appUserService).syncFromLogto("u2", null, null, "bob");
        verify(appUserService).syncFromLogto("u2", "bob@example.com", "Bob", "bob");
    }

    @Test
    void userDeletedInLogtoIsDeleted() throws Exception {
        send("""
                {"hookId":"h1","event":"User.Deleted","createdAt":"2026-09-27T20:02:00.000Z",
                 "path":"/users/u3","method":"DELETE","status":204,"params":{"userId":"u3"},
                 "matchedRoute":"/users/:userId","data":null}
                """).andExpect(status().isNoContent());

        verify(appUserService).deleteByLogtoId("u3");
    }

    @Test
    void otherEventsAreAcknowledgedAndIgnored() throws Exception {
        send("""
                {"hookId":"h1","event":"PostSignIn","createdAt":"2026-09-27T20:03:00.000Z","userId":"u1"}
                """).andExpect(status().isNoContent());

        verifyNoInteractions(appUserService);
    }

    @Test
    void requestsWithoutAValidSignatureAreRejected() throws Exception {
        String body = """
                {"event":"User.Deleted","params":{"userId":"u1"}}""";

        mvc.perform(post("/webhooks/logto").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/logto").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(LogtoWebhookSignature.HEADER, sign("{\"event\":\"PostSignIn\"}")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(appUserService);
    }

    /** No session, no CSRF token, no sign-in: the signature alone lets the request in. */
    private ResultActions send(String body) throws Exception {
        return mvc.perform(post("/webhooks/logto")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(LogtoWebhookSignature.HEADER, sign(body)));
    }

    /** What Logto does, computed independently of LogtoWebhookSignature. */
    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
