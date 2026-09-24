package com.gucardev.eronetoone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.eronetoone.idcard.IDCardRepository;
import com.gucardev.eronetoone.person.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PersonIdCardApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private IDCardRepository idCardRepository;

    @BeforeEach
    void clean() {
        personRepository.deleteAll();
    }

    private static String person(String name, String cardNumber) {
        String card = cardNumber == null ? "" : """
                , "idCard": {"cardNumber": "%s", "expiryDate": "2099-01-01"}""".formatted(cardNumber);
        return "{\"name\": \"%s\"%s}".formatted(name, card);
    }

    private static String card(String cardNumber) {
        return "{\"cardNumber\": \"%s\", \"expiryDate\": \"2099-01-01\"}".formatted(cardNumber);
    }

    private long createPerson(String name, String cardNumber) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/persons")
                        .contentType(MediaType.APPLICATION_JSON).content(person(name, cardNumber)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    @Test
    void createsPersonTogetherWithCard() throws Exception {
        mockMvc.perform(post("/api/persons").contentType(MediaType.APPLICATION_JSON)
                        .content(person("Ada", "TR-001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ada"))
                .andExpect(jsonPath("$.idCard.cardNumber").value("TR-001"))
                .andExpect(jsonPath("$.idCard.personId").isNumber());
    }

    @Test
    void createsPersonWithoutCard() throws Exception {
        mockMvc.perform(post("/api/persons").contentType(MediaType.APPLICATION_JSON)
                        .content(person("Bob", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idCard").doesNotExist());
    }

    @Test
    void rejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/persons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"idCard\": {\"cardNumber\": \"\", \"expiryDate\": \"2000-01-01\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void rejectsDuplicateCardNumberOnCreate() throws Exception {
        createPerson("Ada", "TR-001");

        mockMvc.perform(post("/api/persons").contentType(MediaType.APPLICATION_JSON)
                        .content(person("Eve", "TR-001")))
                .andExpect(status().isConflict());
        assertThat(personRepository.count()).isEqualTo(1);
    }

    @Test
    void listsPersonsWithTheirCards() throws Exception {
        createPerson("Ada", "TR-001");
        createPerson("Bob", null);

        mockMvc.perform(get("/api/persons?sort=name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].idCard.cardNumber").value("TR-001"))
                .andExpect(jsonPath("$.content[1].idCard").doesNotExist());
    }

    @Test
    void updatesPersonNameWithoutTouchingCard() throws Exception {
        long id = createPerson("Ada", "TR-001");

        mockMvc.perform(put("/api/persons/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ada Lovelace\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.idCard.cardNumber").value("TR-001"));
    }

    @Test
    void returns404ForUnknownPerson() throws Exception {
        mockMvc.perform(get("/api/persons/999")).andExpect(status().isNotFound());
    }

    @Test
    void deletingPersonCascadesToCard() throws Exception {
        long id = createPerson("Ada", "TR-001");

        mockMvc.perform(delete("/api/persons/" + id)).andExpect(status().isNoContent());

        assertThat(idCardRepository.count()).isZero();
    }

    @Test
    void putCreatesCardThenReplacesItInPlace() throws Exception {
        long id = createPerson("Bob", null);

        mockMvc.perform(put("/api/persons/" + id + "/id-card").contentType(MediaType.APPLICATION_JSON)
                        .content(card("TR-002")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardNumber").value("TR-002"));

        mockMvc.perform(put("/api/persons/" + id + "/id-card").contentType(MediaType.APPLICATION_JSON)
                        .content(card("TR-003")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardNumber").value("TR-003"));

        assertThat(idCardRepository.count()).isEqualTo(1);
    }

    @Test
    void putRejectsCardNumberOwnedBySomeoneElse() throws Exception {
        createPerson("Ada", "TR-001");
        long bob = createPerson("Bob", "TR-002");

        mockMvc.perform(put("/api/persons/" + bob + "/id-card").contentType(MediaType.APPLICATION_JSON)
                        .content(card("TR-001")))
                .andExpect(status().isConflict());
    }

    @Test
    void putAllowsResubmittingTheSameCardNumber() throws Exception {
        long id = createPerson("Ada", "TR-001");

        mockMvc.perform(put("/api/persons/" + id + "/id-card").contentType(MediaType.APPLICATION_JSON)
                        .content(card("TR-001")))
                .andExpect(status().isOk());
    }

    @Test
    void getAndDeleteCardOfPerson() throws Exception {
        long id = createPerson("Ada", "TR-001");

        mockMvc.perform(get("/api/persons/" + id + "/id-card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardNumber").value("TR-001"));

        mockMvc.perform(delete("/api/persons/" + id + "/id-card")).andExpect(status().isNoContent());

        assertThat(idCardRepository.count()).isZero();
        assertThat(personRepository.count()).isEqualTo(1);
        mockMvc.perform(get("/api/persons/" + id + "/id-card")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/persons/" + id + "/id-card")).andExpect(status().isNotFound());
    }

    @Test
    void looksUpOwnerFromTheCardNumber() throws Exception {
        long id = createPerson("Ada", "TR-001");

        mockMvc.perform(get("/api/id-cards/TR-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personId").value(id));
        mockMvc.perform(get("/api/id-cards/NOPE")).andExpect(status().isNotFound());
    }
}
