package com.gucardev.jackson.dto;

// channel/summary prove the request body was deserialized into the correct concrete
// subtype (not just "some Notification") - see JacksonController.dispatch.
public record DispatchResult(String channel, String summary) {}
