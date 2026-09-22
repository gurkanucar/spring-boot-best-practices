package com.gucardev.jackson.web;

import com.gucardev.jackson.dto.ApiKeyRequest;
import com.gucardev.jackson.dto.CardPaymentRequest;
import com.gucardev.jackson.dto.Contact;
import com.gucardev.jackson.dto.CouponRequest;
import com.gucardev.jackson.dto.DayRange;
import com.gucardev.jackson.dto.DispatchResult;
import com.gucardev.jackson.dto.EmailNotification;
import com.gucardev.jackson.dto.EventSchedule;
import com.gucardev.jackson.dto.LegacyOrderRequest;
import com.gucardev.jackson.dto.LoginRequest;
import com.gucardev.jackson.dto.Money;
import com.gucardev.jackson.dto.Notification;
import com.gucardev.jackson.dto.PaymentStatus;
import com.gucardev.jackson.dto.Ping;
import com.gucardev.jackson.dto.SmsNotification;
import com.gucardev.jackson.dto.UserProfile;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jackson")
public class JacksonController {

    private final AtomicLong sequence = new AtomicLong();

    @PostMapping("/profile")
    public UserProfile profile(@RequestBody UserProfile request) {
        // request.id() is ignored on the way in (@JsonProperty READ_ONLY) - the server
        // assigns its own regardless of what the client sent. request.internalRiskScore()
        // is also always null here (@JsonIgnore), no matter what the client sent for it -
        // and the 99 set below never reaches the response either.
        return new UserProfile(
                sequence.incrementAndGet(), request.fullName(), request.nickname(), request.birthDate(),
                Instant.now(), 99);
    }

    @PostMapping("/login")
    public LoginRequest login(@Valid @RequestBody LoginRequest request) {
        // password is WRITE_ONLY - echoing the whole record back never leaks it.
        return request;
    }

    @PostMapping("/orders/legacy")
    public LegacyOrderRequest legacyOrder(@Valid @RequestBody LegacyOrderRequest request) {
        return request;
    }

    @PostMapping("/payments")
    public CardPaymentRequest payment(@Valid @RequestBody CardPaymentRequest request) {
        return request;
    }

    // A raw JSON string body ("paid"), not a path variable - path variables are converted
    // by Spring's own ConversionService, which never sees @JsonCreator/@JsonValue.
    @PostMapping("/payments/status")
    public PaymentStatus paymentStatus(@RequestBody PaymentStatus status) {
        return status;
    }

    // Response direction: the server picks which concrete subtype to write, and
    // @JsonTypeInfo/@JsonSubTypes add the "type" discriminator so the client can tell them
    // apart without guessing from shape alone.
    @GetMapping("/notifications")
    public List<Notification> notifications() {
        return List.of(
                new EmailNotification("demo@example.com", "Welcome"),
                new SmsNotification("+905551112233", "Your code is 4242"));
    }

    // Request direction: the client sends the "type" discriminator, and Jackson picks
    // which record to construct - EmailNotification or SmsNotification - before this
    // method even runs. The exhaustive switch over the sealed interface is what proves it:
    // this only compiles if every permitted subtype is handled, and to().../phoneNumber()
    // are only reachable once the concrete type is known.
    @PostMapping("/notifications/dispatch")
    public DispatchResult dispatch(@RequestBody Notification notification) {
        return switch (notification) {
            case EmailNotification email ->
                new DispatchResult("email", "to " + email.to() + ": " + email.subject());
            case SmsNotification sms ->
                new DispatchResult("sms", "to " + sms.phoneNumber() + ": " + sms.message());
        };
    }

    // Round-trips every java.time shape untouched, so the response shows exactly what
    // Jackson does by default (or via @JsonFormat) for date-only, time-only, date+time
    // with/without an offset, an absolute instant, and a duration - side by side.
    @PostMapping("/events")
    public EventSchedule event(@RequestBody EventSchedule request) {
        return request;
    }

    // @RequestParam (and @PathVariable) never go through Jackson at all - they're bound by
    // Spring MVC's own ConversionService, and @DateTimeFormat (org.springframework.format
    // .annotation, NOT Jackson's @JsonFormat) is what controls their pattern. This is also
    // why PaymentStatus above needs a request body instead of a @PathVariable: this
    // pipeline never looks at @JsonCreator/@JsonValue either - the two binding mechanisms
    // are entirely separate, even though both end up producing the same Java type.
    //
    // The scenario this simulates: the database column behind "date" is a LocalDateTime,
    // but callers only think in terms of a calendar day - so the controller accepts a
    // plain LocalDate and expands it to the half-open [start, end) window a LocalDateTime
    // query would actually need.
    @GetMapping("/events/day-range")
    public DayRange dayRange(@RequestParam @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        return new DayRange(date, start, start.plusDays(1));
    }

    // Same mechanism as day-range above, but the query parameter is a full LocalDateTime
    // this time, not just a LocalDate - "dd/MM/yyyy HH:mm" governs parsing the INPUT
    // (Spring's ConversionService), while the plain ISO shape in the response is Jackson's
    // default LocalDateTime rendering on the way OUT. Two different mechanisms, chained
    // back to back, on what looks like a single "just reformat the date" request.
    @GetMapping("/events/at")
    public LocalDateTime eventAt(
            @RequestParam @DateTimeFormat(pattern = "dd/MM/yyyy HH:mm") LocalDateTime at) {
        return at;
    }

    // Same local-converter shape as CardPaymentRequest.cardNumber above, but for a
    // completely different transform: uppercasing, not masking. "save10", "SAVE10" and
    // "SaVe10" all normalize to the one canonical "SAVE10" on the way in, and stay
    // uppercase on the way out even if something upstream ever stores it lowercase.
    // Locale.ROOT in the converter matters here in a way masking never has to worry
    // about - see UppercaseSerializer's comment for the Turkish-locale "i" gotcha.
    @PostMapping("/coupons")
    public CouponRequest coupon(@Valid @RequestBody CouponRequest request) {
        return request;
    }

    // A partial mask - the opposite shape from CardPaymentRequest.cardNumber, which keeps
    // the LAST four digits. Here the first few characters (the part a human uses to
    // recognize which key this is) stay visible, and everything after that is "*" - with
    // no deserializer at all, because there's nothing to unmask; the real key is only
    // ever sent once, by whatever issued it.
    @PostMapping("/api-keys")
    public ApiKeyRequest apiKey(@Valid @RequestBody ApiKeyRequest request) {
        return request;
    }

    @PostMapping("/contacts")
    public Contact contact(@Valid @RequestBody Contact request) {
        return request;
    }

    @GetMapping("/money")
    public Money money() {
        return new Money(new BigDecimal("100.50"), "USD");
    }

    @GetMapping("/ping")
    public Ping ping() {
        return new Ping();
    }
}
