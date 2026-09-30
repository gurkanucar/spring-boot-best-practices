package com.gucardev.jwtauthrefreshtokenroles.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.jwtauthrefreshtokenroles.otp.delivery.EmailOtpSender;
import com.gucardev.jwtauthrefreshtokenroles.otp.delivery.OtpMessage;
import com.gucardev.jwtauthrefreshtokenroles.otp.delivery.OtpSender;
import com.gucardev.jwtauthrefreshtokenroles.otp.delivery.SmsOtpSender;
import com.gucardev.jwtauthrefreshtokenroles.otp.store.OneTimeCodeRepository;
import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleName;
import com.gucardev.jwtauthrefreshtokenroles.role.repository.RoleRepository;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import com.gucardev.jwtauthrefreshtokenroles.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared context for API tests. Every test uses fresh, random e-mail addresses and phone numbers, so
 * no cleanup is needed. Mail is not sent: JavaMailSender is a mock, but the real EmailOtpSender and
 * Thymeleaf templates run. Both senders are spies, so tests read the delivered link tokens and codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    protected static final String ADMIN_EMAIL = "admin@example.com";
    protected static final String ADMIN_PASSWORD = "admin12345";
    protected static final String PASSWORD = "password123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected JwtDecoder jwtDecoder;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected OneTimeCodeRepository oneTimeCodeRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @MockitoBean
    protected JavaMailSender javaMailSender;

    @MockitoSpyBean
    protected EmailOtpSender emailOtpSender;

    @MockitoSpyBean
    protected SmsOtpSender smsOtpSender;

    @BeforeEach
    void stubMailSender() {
        when(javaMailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /** A random E.164 number in the +90 555 range. */
    protected static String uniquePhone() {
        return "+90555" + ThreadLocalRandom.current().nextInt(1_000_000, 10_000_000);
    }

    protected UserEntity createUser(String email, String password, boolean emailVerified, String... roles) {
        return transactionTemplate.execute(status -> {
            UserEntity user = UserEntity.create(email, passwordEncoder.encode(password), Instant.now());
            user.setEmailVerified(emailVerified);
            for (String role : roles) {
                user.addRole(roleRepository.findByName(role).orElseThrow());
            }
            return userRepository.save(user);
        });
    }

    /** A verified USER whose phone is already verified, password = PASSWORD. */
    protected UserEntity createUserWithVerifiedPhone(String email, String phone) {
        return transactionTemplate.execute(status -> {
            UserEntity user = UserEntity.create(email, passwordEncoder.encode(PASSWORD), Instant.now());
            user.setEmailVerified(true);
            user.setPhone(phone);
            user.addRole(roleRepository.findByName(RoleName.USER).orElseThrow());
            return userRepository.save(user);
        });
    }

    /** Moves the creation time of all the user's codes into the past, e.g. to get past the cooldown. */
    protected void ageCodes(UUID userId, Duration by) {
        transactionTemplate.executeWithoutResult(status -> oneTimeCodeRepository.findAll().stream()
                .filter(code -> code.getUserId().equals(userId))
                .forEach(code -> code.setCreatedAt(code.getCreatedAt().minus(by))));
    }

    protected List<OtpMessage> sentEmails(String target) {
        return sent(emailOtpSender, target);
    }

    protected List<OtpMessage> sentSms(String phone) {
        return sent(smsOtpSender, phone);
    }

    protected OtpMessage lastEmail(String target) {
        List<OtpMessage> messages = sentEmails(target);
        assertThat(messages).as("e-mails sent to " + target).isNotEmpty();
        return messages.getLast();
    }

    protected OtpMessage lastSms(String phone) {
        List<OtpMessage> messages = sentSms(phone);
        assertThat(messages).as("SMS sent to " + phone).isNotEmpty();
        return messages.getLast();
    }

    private static List<OtpMessage> sent(OtpSender sender, String target) {
        return Mockito.mockingDetails(sender).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("send"))
                .map(invocation -> (OtpMessage) invocation.getArgument(0))
                .filter(message -> message.target().equals(target))
                .toList();
    }

    /** JSON object from alternating keys and values; values may be null. */
    protected String json(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return jsonMapper.writeValueAsString(map);
    }

    protected ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions postJson(String url, String body, String accessToken) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", bearer(accessToken)));
    }

    protected Tokens login(String email, String password) throws Exception {
        MvcResult result = postJson("/api/auth/login", json("email", email, "password", password))
                .andExpect(status().isOk())
                .andReturn();
        return Tokens.from(result);
    }

    protected ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/auth/refresh", json("refreshToken", refreshToken));
    }

    protected static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    protected record Tokens(String accessToken, String refreshToken) {

        public static Tokens from(MvcResult result) throws Exception {
            String body = result.getResponse().getContentAsString();
            return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
        }
    }
}
