package com.gucardev.logtobasicsecurity.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;

/**
 * The parts of a Logto webhook payload this application uses. Where the user is depends on the event:
 * <ul>
 *   <li>{@code PostRegister} (a user signed up): {@code user}</li>
 *   <li>{@code User.Created}, {@code User.Data.Updated}: {@code data}</li>
 *   <li>{@code User.Deleted}: {@code data} is null, the id is in {@code params.userId} (the path
 *       parameter of Logto's {@code DELETE /api/users/:userId})</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LogtoWebhookEvent(
        String hookId,
        String event,
        Instant createdAt,
        User user,
        User data,
        Map<String, Object> params) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(String id, String username, String primaryEmail, String name) {
    }
}
