package com.gucardev.slf4jlogging.examples;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SafeLoggingExamples {

    public void loginAttempt(String email) {
        // Log selected, masked fields. Never log the request DTO, password or token.
        log.atInfo().addKeyValue("event", "login.demo")
                .addKeyValue("emailMasked", maskEmail(email))
                .log("Simulated login attempt; no authentication performed");
    }

    public void userSuppliedText(String text) {
        // Placeholders do not prevent embedded newlines from forging text log entries.
        log.info("User supplied note={}", oneLine(text));
    }

    static String maskEmail(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        return at < 0 ? "[REDACTED]" : "***@" + oneLine(email.substring(at + 1));
    }

    static String oneLine(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder();
        value.codePoints().limit(120).forEach(codePoint -> safe.appendCodePoint(
                Character.isISOControl(codePoint) || codePoint == 0x2028 || codePoint == 0x2029
                        ? '_' : codePoint));
        return safe.toString();
    }
}
