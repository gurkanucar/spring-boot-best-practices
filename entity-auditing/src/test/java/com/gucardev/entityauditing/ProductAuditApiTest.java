package com.gucardev.entityauditing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProductAuditApiTest {

    @Autowired
    private MockMvc mockMvc;

    private long createCategory(String name) throws Exception {
        String body = mockMvc.perform(post("/api/categories").header("X-User", "admin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String product(String name, String price, int stock, long categoryId) {
        return "{\"name\":\"%s\",\"price\":%s,\"stock\":%d,\"categoryId\":%d}".formatted(name, price, stock, categoryId);
    }

    private long createProduct(String user, String json) throws Exception {
        String body = mockMvc.perform(post("/api/products").header("X-User", user)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void update(long id, String user, String json) throws Exception {
        mockMvc.perform(put("/api/products/" + id).header("X-User", user)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
    }

    private long revisionAt(long productId, int index) throws Exception {
        String body = mockMvc.perform(get("/api/products/" + productId + "/history")).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$[" + index + "].revision")).longValue();
    }

    @Test
    void fullLifecycleIsRecordedWithWhoWhatAndWhen() throws Exception {
        long cat = createCategory("Peripherals");
        long id = createProduct("alice", product("Keyboard", "49.90", 10, cat));
        update(id, "bob", product("Keyboard", "39.90", 10, cat));
        update(id, "bob", product("Keyboard", "39.90", 7, cat));
        mockMvc.perform(delete("/api/products/" + id).header("X-User", "carol")).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/" + id + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].type", contains("CREATED", "UPDATED", "UPDATED", "DELETED")))
                .andExpect(jsonPath("$[*].changedBy", contains("alice", "bob", "bob", "carol")))
                .andExpect(jsonPath("$[0].state.price").value(49.90))
                .andExpect(jsonPath("$[1].changedFields", contains("price")))
                .andExpect(jsonPath("$[1].state.price").value(39.90))
                .andExpect(jsonPath("$[2].changedFields", contains("stock")))
                // store_data_at_delete: the DELETE revision still shows what was deleted
                .andExpect(jsonPath("$[3].state.name").value("Keyboard"))
                .andExpect(jsonPath("$[3].state.stock").value(7));
    }

    @Test
    void deletedProductIsGoneButItsHistoryAndLastStateRemain() throws Exception {
        long cat = createCategory("Cameras");
        long id = createProduct("alice", product("Camera", "300", 2, cat));
        Thread.sleep(10);
        Instant beforeDelete = Instant.now();
        mockMvc.perform(delete("/api/products/" + id).header("X-User", "carol")).andExpect(status().isNoContent());
        long deleteRevision = revisionAt(id, 1);

        // the product itself is gone
        mockMvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/products/" + id + "/revert/" + revisionAt(id, 0))).andExpect(status().isNotFound());

        // ...but it is listed as deleted, with who, when and its last state
        mockMvc.perform(get("/api/products/deleted"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].deletedBy", contains("carol")))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].revision", contains((int) deleteRevision)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].lastState.name", contains("Camera")))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].lastState.categoryName", contains("Cameras")));

        // the DELETE revision itself, and the state just before it
        mockMvc.perform(get("/api/products/" + id + "/revisions/" + deleteRevision))
                .andExpect(jsonPath("$.type").value("DELETED"))
                .andExpect(jsonPath("$.changedBy").value("carol"))
                .andExpect(jsonPath("$.state.price").value(300));
        mockMvc.perform(get("/api/products/" + id + "/as-of").param("at", beforeDelete.toString()))
                .andExpect(jsonPath("$.name").value("Camera"));
        mockMvc.perform(get("/api/products/" + id + "/as-of").param("at", Instant.now().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("did not exist")));
    }

    @Test
    void savingTheSameValuesCreatesNoRevision() throws Exception {
        long cat = createCategory("Office");
        long id = createProduct("alice", product("Stapler", "5", 3, cat));

        update(id, "bob", product("Stapler", "5.00", 3, cat));

        mockMvc.perform(get("/api/products/" + id + "/history")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void changingOnlyANotAuditedFieldCreatesNoRevision() throws Exception {
        long cat = createCategory("Office 2");
        long id = createProduct("alice", product("Pen", "1", 100, cat));

        mockMvc.perform(post("/api/products/" + id + "/view")).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/" + id + "/history")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void priceHistoryListsOnlyRevisionsThatChangedThePrice() throws Exception {
        long cat = createCategory("Garden");
        long id = createProduct("alice", product("Hose", "10", 5, cat));
        update(id, "bob", product("Garden hose", "10", 5, cat));   // name only
        update(id, "bob", product("Garden hose", "12", 5, cat));   // price
        update(id, "carol", product("Garden hose", "12", 4, cat)); // stock only

        mockMvc.perform(get("/api/products/" + id + "/price-history"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].price", contains(10.0, 12.0)))
                .andExpect(jsonPath("$[*].changedBy", contains("alice", "bob")));
    }

    @Test
    void oldRevisionsShowTheCategoryAsItWasThen() throws Exception {
        long cat = createCategory("Audio");
        long id = createProduct("alice", product("Speaker", "80", 2, cat));

        mockMvc.perform(put("/api/categories/" + cat).header("X-User", "admin")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sound\"}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.categoryName").value("Sound"));
        mockMvc.perform(get("/api/products/" + id + "/history"))
                .andExpect(jsonPath("$[0].state.categoryName").value("Audio"));
    }

    @Test
    void singleRevisionThroughSpringDataAndRevertToIt() throws Exception {
        long cat = createCategory("Kitchen");
        long id = createProduct("alice", product("Kettle", "100", 1, cat));
        update(id, "bob", product("Kettle", "80", 1, cat));
        long firstRevision = revisionAt(id, 0);

        mockMvc.perform(get("/api/products/" + id + "/revisions/" + firstRevision))
                .andExpect(jsonPath("$.type").value("CREATED"))
                .andExpect(jsonPath("$.changedBy").value("alice"))
                .andExpect(jsonPath("$.state.price").value(100));

        mockMvc.perform(post("/api/products/" + id + "/revert/" + firstRevision).header("X-User", "carol"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(100));

        // Reverting is a new change: history grows, nothing is rewritten.
        mockMvc.perform(get("/api/products/" + id + "/history"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].changedBy").value("carol"))
                .andExpect(jsonPath("$[2].changedFields", contains("price")));
        mockMvc.perform(get("/api/products/" + id + "/revisions/999999")).andExpect(status().isNotFound());
    }

    @Test
    void stateAtAPointInTime() throws Exception {
        long cat = createCategory("Toys");
        long id = createProduct("alice", product("Ball", "3", 50, cat));
        Thread.sleep(10);
        Instant between = Instant.now();
        Thread.sleep(10);
        update(id, "bob", product("Ball", "4", 50, cat));

        mockMvc.perform(get("/api/products/" + id + "/as-of").param("at", between.toString()))
                .andExpect(jsonPath("$.price").value(3));
        mockMvc.perform(get("/api/products/" + id + "/as-of").param("at", Instant.now().toString()))
                .andExpect(jsonPath("$.price").value(4));
        mockMvc.perform(get("/api/products/" + id + "/as-of").param("at", Instant.EPOCH.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void springDataAuditingKeepsCreatorAndLastModifierOnTheRow() throws Exception {
        long cat = createCategory("Books");
        long id = createProduct("alice", product("Novel", "15", 9, cat));
        update(id, "bob", product("Novel", "14", 9, cat));

        String body = mockMvc.perform(get("/api/products/" + id))
                .andExpect(jsonPath("$.createdBy").value("alice"))
                .andExpect(jsonPath("$.lastModifiedBy").value("bob"))
                .andReturn().getResponse().getContentAsString();
        Instant created = Instant.parse(JsonPath.read(body, "$.createdAt"));
        Instant modified = Instant.parse(JsonPath.read(body, "$.lastModifiedAt"));
        assertThat(modified).isAfterOrEqualTo(created);
    }

    @Test
    void revisionShowsWhoAndWhichEntityTypesChanged() throws Exception {
        long cat = createCategory("Sports");
        long id = createProduct("dave", product("Racket", "60", 3, cat));

        mockMvc.perform(get("/api/revisions/" + revisionAt(id, 0)))
                .andExpect(jsonPath("$.changedBy").value("dave"))
                .andExpect(jsonPath("$.modifiedEntities", contains("com.gucardev.entityauditing.product.Product")));
    }

    @Test
    void requestWithoutAUserIsRecordedAsAnonymous() throws Exception {
        long cat = createCategory("Misc");
        String body = mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(product("Thing", "1", 1, cat)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(get("/api/products/" + id + "/history"))
                .andExpect(jsonPath("$[*].changedBy", hasItem("anonymous")));
    }

    @Test
    void unknownProductHasNoHistory() throws Exception {
        mockMvc.perform(get("/api/products/999999/history")).andExpect(status().isNotFound());
    }
}
