package com.gucardev.logtobasicsecurity.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * Receives Logto webhooks. Not behind sign-in (Logto has no session here): the signature is what
 * proves the request comes from Logto. {@code SecurityConfig} permits the path and skips CSRF for it.
 *
 * <p>Handled synchronously: a database update is quick, and answering 2xx only after it succeeded
 * means a failure reaches Logto as an error, where it shows in the webhook's delivery log (and can
 * be retried). Handing it to a background thread would answer 2xx before knowing.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class LogtoWebhookController {

    private final LogtoWebhookSignature signature;
    private final LogtoUserSync userSync;
    private final JsonMapper jsonMapper;

    /** {@code byte[]}, not a parsed object: the signature is checked over the exact bytes Logto sent. */
    @PostMapping("/webhooks/logto")
    public ResponseEntity<Void> receive(@RequestHeader(name = LogtoWebhookSignature.HEADER, required = false) String sig,
                                        @RequestBody byte[] body) {
        if (!signature.isConfigured()) {
            log.warn("Logto webhook rejected: app.logto.webhook-signing-key (LOGTO_WEBHOOK_SIGNING_KEY) is not set");
            return ResponseEntity.status(503).build();
        }
        if (!signature.isValid(body, sig)) {
            log.warn("Logto webhook rejected: invalid signature");
            return ResponseEntity.status(401).build();
        }
        userSync.handle(jsonMapper.readValue(body, LogtoWebhookEvent.class));
        return ResponseEntity.noContent().build();
    }
}
