package com.gucardev.logbookloggingrequests.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint here does nothing interesting on its own; the point is what
 * Logbook logs around the call. Toggle {@code logbook.filter.enabled} (see
 * application-dev.yaml / application-prod.yaml) and hit these with a body to
 * see the effect.
 */
@RestController
@RequestMapping("/api/logbook")
public class LogbookController {

    public record EchoRequest(@NotBlank String message) {
    }

    public record EchoResponse(String message, int length) {
    }

    public record SensitiveRequest(@NotBlank String username, String password, String apiKey, String token) {
    }

    public record Ack(String result) {
    }

    public record LargePayloadRequest(@NotBlank String text) {
    }

    @PostMapping("/echo")
    public EchoResponse echo(@Valid @RequestBody EchoRequest request) {
        // Nothing sensitive here: this is what an unobfuscated Logbook entry looks like.
        return new EchoResponse(request.message(), request.message().length());
    }

    @PostMapping("/sensitive")
    public Ack sensitive(@Valid @RequestBody SensitiveRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // password/apiKey/token and the Authorization header never appear in application
        // code as anything but opaque strings - obfuscation happens entirely in Logbook's
        // HTTP layer (logbook.obfuscate.*), before this method ever runs.
        return new Ack("sensitive");
    }

    @GetMapping("/status/{code}")
    public ResponseEntity<Ack> status(@PathVariable @Min(200) @Max(599) int code) {
        // Lets you trigger any response status on demand, to see logbook.strategy:
        // status-at-least / minimum-status decide whether Logbook logs this call at all.
        return ResponseEntity.status(HttpStatus.valueOf(code)).body(new Ack("status-" + code));
    }

    @PostMapping("/large")
    public Ack large(@Valid @RequestBody LargePayloadRequest request) {
        // A body larger than logbook.write.max-body-size demonstrates truncation
        // in the logged request, independent of what the controller actually receives.
        return new Ack("received " + request.text().length() + " chars");
    }
}
