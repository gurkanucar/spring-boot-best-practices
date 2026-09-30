package com.gucardev.jwtauthrefreshtokenroles.otp.service;

import com.gucardev.jwtauthrefreshtokenroles.common.config.OtpProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** HMAC-SHA256(otp.pepper, userId:purpose:channel:code). The pepper never touches the database. */
@Component
public class HmacOtpCodeHasher implements OtpCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public HmacOtpCodeHasher(OtpProperties otpProperties) {
        this.key = new SecretKeySpec(otpProperties.pepper().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    @Override
    public String hash(UUID userId, OtpPurpose purpose, OtpChannel channel, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            String input = userId + ":" + purpose + ":" + channel + ":" + code;
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
