package com.gucardev.jwtauthrefreshtokenroles.otp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.jwtauthrefreshtokenroles.common.config.OtpProperties;
import com.gucardev.jwtauthrefreshtokenroles.common.security.Sha256TokenHasher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class HmacOtpCodeHasherTest {

    private static final String PEPPER = "p".repeat(32);
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static HmacOtpCodeHasher hasher(String pepper) {
        OtpProperties.Ttl ttl = new OtpProperties.Ttl(Duration.ofMinutes(15), Duration.ofHours(24), Duration.ofMinutes(5));
        return new HmacOtpCodeHasher(new OtpProperties(pepper, Duration.ofSeconds(60), 5, ttl));
    }

    @Test
    void isHmacSha256OverUserPurposeChannelAndCode() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(PEPPER.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(mac.doFinal(
                (USER + ":PASSWORD_RESET:SMS:123456").getBytes(StandardCharsets.UTF_8)));

        assertThat(hasher(PEPPER).hash(USER, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS, "123456"))
                .isEqualTo(expected);
    }

    @Test
    void isNotAPlainHashOfTheCode() {
        assertThat(hasher(PEPPER).hash(USER, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS, "123456"))
                .isNotEqualTo(new Sha256TokenHasher().hash("123456"));
    }

    @Test
    void dependsOnPepperAndContext() {
        String base = hasher(PEPPER).hash(USER, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS, "123456");

        assertThat(hasher("q".repeat(32)).hash(USER, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS, "123456"))
                .isNotEqualTo(base);
        assertThat(hasher(PEPPER).hash(USER, OtpPurpose.PHONE_VERIFICATION, OtpChannel.SMS, "123456"))
                .isNotEqualTo(base);
        assertThat(hasher(PEPPER).hash(UUID.randomUUID(), OtpPurpose.PASSWORD_RESET, OtpChannel.SMS, "123456"))
                .isNotEqualTo(base);
    }
}
