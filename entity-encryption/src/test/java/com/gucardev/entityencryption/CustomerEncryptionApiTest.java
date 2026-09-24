package com.gucardev.entityencryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.entityencryption.crypto.AesGcmCipher;
import com.gucardev.entityencryption.crypto.EncryptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerEncryptionApiTest {

    private static final String BODY = """
            {"name": "Ann", "email": "%s", "nationalId": "12345678901", "phone": "+90 555 000 11 22"}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private EncryptionProperties properties;

    @BeforeEach
    void clean() {
        jdbc.sql("delete from customer").update();
    }

    private ResultActions create(String email) throws Exception {
        return mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(email)));
    }

    private long createAndGetId(String email) throws Exception {
        String body = create(email).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private String raw(String column, long id) {
        return jdbc.sql("select " + column + " from customer where id = :id").param("id", id)
                .query(String.class).single();
    }

    @Test
    void apiSeesPlaintextButTheDatabaseOnlySeesCiphertext() throws Exception {
        long id = createAndGetId("ann@mail.com");

        mockMvc.perform(get("/api/customers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ann@mail.com"))
                .andExpect(jsonPath("$.nationalId").value("12345678901"))
                .andExpect(jsonPath("$.phone").value("+90 555 000 11 22"));

        assertThat(raw("name", id)).isEqualTo("Ann");
        assertThat(raw("email", id)).startsWith("v1:k2:").doesNotContain("ann@mail.com");
        assertThat(raw("national_id", id)).startsWith("v1:k2:").doesNotContain("12345678901");
        assertThat(raw("phone", id)).startsWith("v1:k2:").doesNotContain("555");
    }

    @Test
    void equalValuesInDifferentRowsGiveDifferentCiphertext() throws Exception {
        long a = createAndGetId("a@mail.com");
        long b = createAndGetId("b@mail.com");

        // Same national id in both rows, yet the stored values differ: no frequency analysis.
        assertThat(raw("national_id", a)).isNotEqualTo(raw("national_id", b));
    }

    @Test
    void nullPhoneStaysNullInTheDatabase() throws Exception {
        String body = mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bob\",\"email\":\"bob@mail.com\",\"nationalId\":\"1\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        assertThat(jdbc.sql("select phone from customer where id = :id").param("id", id)
                .query((rs, i) -> rs.getString(1)).list()).containsExactly((String) null);
    }

    @Test
    void encryptedEmailIsSearchableThroughTheBlindIndex() throws Exception {
        long id = createAndGetId("ann@mail.com");

        mockMvc.perform(get("/api/customers/search").param("email", "  ANN@mail.com "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mockMvc.perform(get("/api/customers/search").param("email", "nobody@mail.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateEmailIsAConflictEvenThoughTheColumnIsEncrypted() throws Exception {
        create("ann@mail.com").andExpect(status().isCreated());

        create("Ann@Mail.com").andExpect(status().isConflict());
    }

    @Test
    void updateReEncryptsAndRefreshesTheBlindIndex() throws Exception {
        long id = createAndGetId("ann@mail.com");
        String before = raw("email", id);

        mockMvc.perform(put("/api/customers/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("new@mail.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@mail.com"));

        assertThat(raw("email", id)).isNotEqualTo(before);
        mockMvc.perform(get("/api/customers/search").param("email", "new@mail.com")).andExpect(status().isOk());
        mockMvc.perform(get("/api/customers/search").param("email", "ann@mail.com")).andExpect(status().isNotFound());
    }

    @Test
    void updateCannotTakeAnotherCustomersEmail() throws Exception {
        create("ann@mail.com").andExpect(status().isCreated());
        long other = createAndGetId("other@mail.com");

        mockMvc.perform(put("/api/customers/" + other).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("ann@mail.com")))
                .andExpect(status().isConflict());
    }

    @Test
    void validationErrorsNeverEchoTheRejectedValue() throws Exception {
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"nationalId\":\"12345678901\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("not-an-email"));
    }

    @Test
    void deleteAndMissingRows() throws Exception {
        long id = createAndGetId("ann@mail.com");

        mockMvc.perform(delete("/api/customers/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/customers/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void rotationMovesOldRowsToTheActiveKeyWithoutChangingTheData() throws Exception {
        long id = createAndGetId("ann@mail.com");

        // Simulate a row written before the rotation: the same values encrypted with the old key k1.
        AesGcmCipher oldCipher = new AesGcmCipher(new EncryptionProperties("k1", properties.keys(), properties.blindIndexKey()));
        jdbc.sql("update customer set email = :e, national_id = :n, phone = :p where id = :id")
                .param("e", oldCipher.encrypt("ann@mail.com"))
                .param("n", oldCipher.encrypt("12345678901"))
                .param("p", oldCipher.encrypt("+90 555 000 11 22"))
                .param("id", id).update();
        assertThat(raw("national_id", id)).startsWith("v1:k1:");
        // still readable through the entity, because k1 stays in the key list
        mockMvc.perform(get("/api/customers/" + id)).andExpect(jsonPath("$.nationalId").value("12345678901"));

        mockMvc.perform(post("/api/admin/key-rotation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reencryptedValues").value(3));

        assertThat(raw("email", id)).startsWith("v1:k2:");
        assertThat(raw("national_id", id)).startsWith("v1:k2:");
        assertThat(raw("phone", id)).startsWith("v1:k2:");
        mockMvc.perform(get("/api/customers/" + id))
                .andExpect(jsonPath("$.email").value("ann@mail.com"))
                .andExpect(jsonPath("$.nationalId").value("12345678901"));
        mockMvc.perform(post("/api/admin/key-rotation")).andExpect(jsonPath("$.reencryptedValues").value(0));
    }

    @Test
    void propertiesUseTwoKeysWithK2Active() {
        assertThat(properties.keys()).containsOnlyKeys("k1", "k2");
        assertThat(properties.activeKeyId()).isEqualTo("k2");
    }
}
