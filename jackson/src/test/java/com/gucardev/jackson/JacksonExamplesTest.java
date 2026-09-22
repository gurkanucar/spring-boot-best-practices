package com.gucardev.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.gucardev.jackson.dto.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Every assertion here replays a real HTTP round trip - each one is proof, not a guess
// about how a Jackson annotation or spring.jackson.* property actually behaves.
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
class JacksonExamplesTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void nullFieldIsOmittedReadOnlyIdIsServerAssignedAndUnknownFieldIsIgnored() {
        // "id" tries to smuggle a client-chosen value; "nickname" is absent; "extra" is a
        // field the DTO never declares at all.
        String requestBody =
                """
                {"id": 999, "fullName": "Ada Lovelace", "birthDate": "10/12/1815",
                 "createdAt": "2024-01-01T00:00:00Z", "extra": "should be ignored"}""";

        JsonNode response = post("/api/jackson/profile", requestBody, 200);

        assertThat(response.has("nickname")).as("null nickname should be omitted, not null").isFalse();
        assertThat(response.get("id").asLong()).as("server assigns its own id").isNotEqualTo(999L);
        assertThat(response.get("birthDate").asString()).isEqualTo("10/12/1815");
        // No @JsonFormat on createdAt: falls back to write-dates-as-timestamps=false, i.e. ISO-8601.
        assertThat(response.get("createdAt").asString()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
    }

    // @JsonIgnore excludes internalRiskScore from BOTH directions - the HTTP round trip
    // above already shows it never appears in the response (even though the controller
    // sets it to 99 server-side), but that alone can't prove the INPUT side was actually
    // dropped rather than just unused. Reading the record straight back out of the
    // ObjectMapper closes that gap.
    @Test
    void jsonIgnoreDropsTheFieldOnBothSerializationAndDeserialization() {
        JsonNode response = post(
                "/api/jackson/profile",
                "{\"fullName\": \"Ada\", \"createdAt\": \"2024-01-01T00:00:00Z\", \"internalRiskScore\": 111}",
                200);
        assertThat(response.has("internalRiskScore")).as("never serialized, no matter its value").isFalse();

        UserProfile deserialized = objectMapper.readValue(
                "{\"fullName\": \"Ada\", \"internalRiskScore\": 111}", UserProfile.class);
        assertThat(deserialized.internalRiskScore())
                .as("dropped before the record was even constructed, not just unused afterward")
                .isNull();
    }

    @Test
    void writeOnlyPasswordNeverAppearsInTheResponse() {
        JsonNode response =
                post("/api/jackson/login", "{\"username\": \"neo\", \"password\": \"matrix\"}", 200);

        assertThat(response.get("username").asString()).isEqualTo("neo");
        assertThat(response.has("password")).isFalse();
    }

    @Test
    void jsonAliasAcceptsTheLegacyFieldName() {
        JsonNode response = post("/api/jackson/orders/legacy", "{\"sku\": \"SKU-1\", \"qty\": 5}", 200);

        assertThat(response.get("quantity").asInt()).isEqualTo(5);
    }

    // The gotcha: this looks like it should make LegacyOrderRequest strict again, but the
    // module-wide fail-on-unknown-properties=false wins regardless - see the comment on
    // LegacyOrderRequest itself for why.
    @Test
    void classLevelIgnoreUnknownFalseDoesNotOverrideTheLenientGlobalDefault() {
        post("/api/jackson/orders/legacy", "{\"sku\": \"SKU-1\", \"quantity\": 5, \"note\": \"typo?\"}", 200);
    }

    @Test
    void customSerializerMasksCardNumberAndCustomDeserializerAcceptsSpaces() {
        JsonNode response = post(
                "/api/jackson/payments",
                "{\"cardNumber\": \"4111 1111 1111 1234\", \"amount\": 19.999}",
                200);

        assertThat(response.get("cardNumber").asString()).isEqualTo("**** **** **** 1234");
        // BigDecimal: exact value survives the round trip, no float rounding.
        assertThat(response.get("amount").asString()).isEqualTo("19.999");
    }

    @Test
    void uppercaseConverterNormalizesAnyInputCasingToTheSameCanonicalCode() {
        assertThat(post("/api/jackson/coupons", "{\"code\": \"save10\"}", 200).get("code").asString())
                .isEqualTo("SAVE10");
        assertThat(post("/api/jackson/coupons", "{\"code\": \"SaVe10\"}", 200).get("code").asString())
                .isEqualTo("SAVE10");
    }

    @Test
    void partialMaskSerializerKeepsOnlyTheFirstThreeCharactersVisible() {
        JsonNode response = post(
                "/api/jackson/api-keys", "{\"name\": \"prod-key\", \"apiKey\": \"sk_live_51Hc9F2abcdef\"}", 200);
        assertThat(response.get("apiKey").asString()).isEqualTo("sk_" + "*".repeat(18));
    }

    @Test
    void partialMaskSerializerMasksEverythingWhenShorterThanTheVisiblePrefix() {
        JsonNode response = post("/api/jackson/api-keys", "{\"name\": \"short\", \"apiKey\": \"ab\"}", 200);
        assertThat(response.get("apiKey").asString()).isEqualTo("**");
    }

    @Test
    void enumRoundTripsThroughItsJsonValueCodeAndAcceptsTheJavaNameToo() {
        assertThat(post("/api/jackson/payments/status", "\"paid\"", 200).asString()).isEqualTo("paid");
        assertThat(post("/api/jackson/payments/status", "\"PAID\"", 200).asString()).isEqualTo("paid");
    }

    @Test
    void unrecognizedEnumValueIsA400NotA500() {
        client.post()
                .uri("/api/jackson/payments/status")
                .contentType(MediaType.APPLICATION_JSON)
                .body("\"bogus\"")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void polymorphicListCarriesATypeDiscriminatorPerSubtype() {
        JsonNode response = get("/api/jackson/notifications", 200);

        assertThat(response.get(0).get("type").asString()).isEqualTo("email");
        assertThat(response.get(0).get("subject").asString()).isEqualTo("Welcome");
        assertThat(response.get(1).get("type").asString()).isEqualTo("sms");
        assertThat(response.get(1).has("phoneNumber")).isTrue();
    }

    // The request-body counterpart of the test above: here the CLIENT sends the "type"
    // discriminator, and Jackson has to pick the right record to construct before the
    // controller's exhaustive switch can even run.
    @Test
    void polymorphicRequestBodyResolvesToTheMatchingSubtype() {
        JsonNode email = post(
                "/api/jackson/notifications/dispatch",
                "{\"type\": \"email\", \"to\": \"demo@example.com\", \"subject\": \"Welcome\"}",
                200);
        assertThat(email.get("channel").asString()).isEqualTo("email");
        assertThat(email.get("summary").asString()).isEqualTo("to demo@example.com: Welcome");

        JsonNode sms = post(
                "/api/jackson/notifications/dispatch",
                "{\"type\": \"sms\", \"phoneNumber\": \"+905551112233\", \"message\": \"Your code is 4242\"}",
                200);
        assertThat(sms.get("channel").asString()).isEqualTo("sms");
    }

    @Test
    void polymorphicRequestBodyWithAnUnknownOrMissingTypeIsA400() {
        client.post()
                .uri("/api/jackson/notifications/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"type\": \"push\", \"token\": \"abc\"}")
                .exchange()
                .expectStatus()
                .isBadRequest();

        client.post()
                .uri("/api/jackson/notifications/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"to\": \"demo@example.com\", \"subject\": \"Welcome\"}")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void jsonUnwrappedFlattensTheNestedAddressBothWaysRoundTrip() {
        JsonNode response = post(
                "/api/jackson/contacts",
                """
                {"name": "Grace Hopper", "street": "1 Infinite Loop", "city": "Norfolk", "zipCode": "23504"}""",
                200);

        assertThat(response.has("address")).as("no nested 'address' object").isFalse();
        assertThat(response.get("street").asString()).isEqualTo("1 Infinite Loop");
        assertThat(response.get("city").asString()).isEqualTo("Norfolk");
    }

    @Test
    void dateOnlyAndCustomFormattedTimeOnlyRoundTripAsPlainCalendarValues() {
        JsonNode response = post(
                "/api/jackson/events",
                """
                {"eventDate": "2026-09-22", "startTime": "14:30", "reminderAt": "2026-09-22T09:00:00",
                 "publishedAt": "2026-09-22T09:00:00+03:00", "createdAt": "2026-09-22T06:00:00Z",
                 "reminderLeadTime": "PT2H30M"}""",
                200);

        // LocalDate: no time-of-day component at all, default ISO rendering.
        assertThat(response.get("eventDate").asString()).isEqualTo("2026-09-22");
        // LocalTime with @JsonFormat("HH:mm"): no date, and no ":00" seconds either.
        assertThat(response.get("startTime").asString()).isEqualTo("14:30");
        // LocalDateTime: date+time, deliberately no zone/offset in or out.
        assertThat(response.get("reminderAt").asString()).isEqualTo("2026-09-22T09:00:00");
        // Duration: ISO-8601 period, not a raw number of seconds.
        assertThat(response.get("reminderLeadTime").asString()).isEqualTo("PT2H30M");
    }

    // The gotcha: OffsetDateTime is NOT rendered with its original offset by default -
    // Jackson 3 normalizes it to the equivalent UTC instant ("Z") on the way out. +03:00
    // and -05:00 both collapse to "Z" here, at two different clock times, because they
    // ARE two different real moments once normalized - proving this isn't a formatting
    // quirk, it's an actual timezone conversion.
    @Test
    void offsetDateTimeIsNormalizedToUtcOnSerializationRegardlessOfTheOriginalOffset() {
        JsonNode plusThree = post(
                "/api/jackson/events",
                """
                {"eventDate": "2026-09-22", "startTime": "14:30", "reminderAt": "2026-09-22T09:00:00",
                 "publishedAt": "2026-09-22T09:00:00+03:00", "createdAt": "2026-09-22T06:00:00Z",
                 "reminderLeadTime": "PT2H30M"}""",
                200);
        assertThat(plusThree.get("publishedAt").asString()).isEqualTo("2026-09-22T06:00:00Z");

        JsonNode minusFive = post(
                "/api/jackson/events",
                """
                {"eventDate": "2026-09-22", "startTime": "14:30", "reminderAt": "2026-09-22T09:00:00",
                 "publishedAt": "2026-09-22T09:00:00-05:00", "createdAt": "2026-09-22T06:00:00Z",
                 "reminderLeadTime": "PT2H30M"}""",
                200);
        assertThat(minusFive.get("publishedAt").asString()).isEqualTo("2026-09-22T14:00:00Z");
    }

    // @DateTimeFormat governs @RequestParam/@PathVariable binding - a completely separate
    // mechanism from Jackson's @JsonFormat used on request-body fields above. "03/04/2026"
    // resolving to April 3rd (not March 4th) proves the dd/MM/yyyy pattern is genuinely
    // being applied, not just falling through to some other default.
    @Test
    void dateTimeFormatPatternGovernsARequestParamNotJacksonAtAll() {
        JsonNode response = get("/api/jackson/events/day-range?date=03/04/2026", 200);
        assertThat(response.get("date").asString()).isEqualTo("2026-04-03");
        assertThat(response.get("rangeStart").asString()).isEqualTo("2026-04-03T00:00:00");
        assertThat(response.get("rangeEnd").asString()).isEqualTo("2026-04-04T00:00:00");
    }

    // The gotcha: @DateTimeFormat(pattern = "dd/MM/yyyy") does NOT reject plain ISO input.
    // Per Spring's own TemporalAccessorParser, when no explicit fallbackPatterns are set,
    // a failed custom-pattern parse silently retries with the type's default ISO parser
    // before giving up - so the "custom pattern" is really "this pattern, OR ISO-8601",
    // never just the one you wrote.
    @Test
    void isoFormatIsSilentlyAcceptedAsAFallbackEvenThoughThePatternIsNotIso() {
        get("/api/jackson/events/day-range?date=2026-09-22", 200);
    }

    @Test
    void aFormatThatMatchesNeitherThePatternNorIsoIsA400() {
        // day=2026 is impossible under dd/MM/yyyy, and slash-separated isn't ISO either.
        client.get()
                .uri("/api/jackson/events/day-range?date=2026/09/22")
                .exchange()
                .expectStatus()
                .isBadRequest();

        client.get()
                .uri("/api/jackson/events/day-range?date=not-a-date")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    // Same @DateTimeFormat mechanism as day-range above, but on a full LocalDateTime
    // parameter instead of just a LocalDate - the custom "dd/MM/yyyy HH:mm" pattern
    // controls parsing the INPUT, while the plain ISO string in the response is Jackson's
    // ordinary LocalDateTime rendering on the way OUT (see EventSchedule.reminderAt above).
    @Test
    void localDateTimeRequestParamIsParsedWithItsOwnDateTimeFormatPattern() {
        String response = get("/api/jackson/events/at?at=22/09/2026 14:30", 200).asString();
        assertThat(response).isEqualTo("2026-09-22T14:30:00");
    }

    // 03/04 resolving to April 3rd (not March 4th) proves dd/MM ordering is genuinely
    // applied to the date PART of a LocalDateTime too, not just to a bare LocalDate.
    @Test
    void localDateTimeRequestParamAppliesDayMonthOrderingNotMonthDay() {
        String response = get("/api/jackson/events/at?at=03/04/2026 15:45", 200).asString();
        assertThat(response).isEqualTo("2026-04-03T15:45:00");
    }

    // Same ISO-fallback gotcha as the LocalDate case above, confirmed for LocalDateTime.
    @Test
    void localDateTimeRequestParamAlsoSilentlyAcceptsIsoAsAFallback() {
        String response = get("/api/jackson/events/at?at=2026-09-22T14:30:00", 200).asString();
        assertThat(response).isEqualTo("2026-09-22T14:30:00");
    }

    @Test
    void localDateTimeRequestParamThatMatchesNeitherFormatIsA400() {
        client.get()
                .uri("/api/jackson/events/at?at=nope")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void globallyRegisteredModuleSerializesMoneyAsACompactString() {
        assertThat(get("/api/jackson/money", 200).asString()).isEqualTo("100.50 USD");
    }

    @Test
    void failOnEmptyBeansDisabledLetsAZeroPropertyBeanSerializeAsAnEmptyObject() {
        JsonNode response = get("/api/jackson/ping", 200);
        assertThat(response.isObject()).isTrue();
        assertThat(response.properties()).isEmpty();
    }

    @Test
    void malformedJsonIsA400ProblemDetailNotA500() {
        client.post()
                .uri("/api/jackson/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{not valid json")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(400);
    }

    private JsonNode post(String uri, String body, int expectedStatus) {
        byte[] response = client.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isEqualTo(expectedStatus)
                .expectBody()
                .returnResult()
                .getResponseBody();
        return objectMapper.readTree(new String(response));
    }

    private JsonNode get(String uri, int expectedStatus) {
        byte[] response = client.get()
                .uri(uri)
                .exchange()
                .expectStatus()
                .isEqualTo(expectedStatus)
                .expectBody()
                .returnResult()
                .getResponseBody();
        return objectMapper.readTree(new String(response));
    }
}
