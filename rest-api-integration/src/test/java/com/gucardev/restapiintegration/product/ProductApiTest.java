package com.gucardev.restapiintegration.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.restapiintegration.IntegrationTestBase;
import com.gucardev.restapiintegration.product.dto.ProductDto;
import com.gucardev.restapiintegration.product.dto.ProductPatchRequest;
import com.gucardev.restapiintegration.product.dto.ProductRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

class ProductApiTest extends IntegrationTestBase {

    @Autowired
    private ProductClient productClient;

    private long create(String name, String price) throws Exception {
        String body = mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"price\":%s}".formatted(name, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void getListsAndReadsByIdOverHttp() throws Exception {
        long id = create("Monitor", "199.00");

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Keyboard"));
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Monitor"))
                .andExpect(jsonPath("$.price").value(199.00));
    }

    @Test
    void postReturnsCreatedWithOurOwnLocation() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cable\",\"price\":5}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/products/")))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void sameIdempotencyKeyCreatesOnlyOnce() throws Exception {
        String first = mockMvc.perform(post("/api/products").header("Idempotency-Key", "order-42")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Lamp\",\"price\":30}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // A retry of the same request (same key) returns the existing product instead of a duplicate.
        String second = mockMvc.perform(post("/api/products").header("Idempotency-Key", "order-42")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Lamp\",\"price\":30}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void putReplacesAndPatchChangesOnlyTheGivenField() throws Exception {
        long id = create("Desk", "300");

        mockMvc.perform(put("/api/products/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Standing desk\",\"price\":450}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Standing desk"))
                .andExpect(jsonPath("$.price").value(450));

        mockMvc.perform(patch("/api/products/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":399.90}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Standing desk"))
                .andExpect(jsonPath("$.price").value(399.90));
    }

    @Test
    void patchBodyLeavesUnsetFieldsOut() {
        String json = JsonMapper.builder().build().writeValueAsString(new ProductPatchRequest(null, new BigDecimal("9.9")));

        assertThat(json).isEqualTo("{\"price\":9.9}");
    }

    @Test
    void deleteThenTheRemote404BecomesOur404() throws Exception {
        long id = create("Old phone", "10");

        mockMvc.perform(delete("/api/products/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product " + id + " not found in the remote API"));
        mockMvc.perform(delete("/api/products/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/products/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"price\":1}")).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/products/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":1}")).andExpect(status().isNotFound());
    }

    @Test
    void invalidInputIsRejectedBeforeAnyRemoteCall() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void queryParameterValuesAreFullyEncoded() throws Exception {
        create("Fish & Chips", "12");

        // If "&" were not encoded, the remote would receive name=" " and return every product.
        assertThat(productClient.list("& Chips"))
                .isNotEmpty()
                .allSatisfy(p -> assertThat(p.name()).contains("& Chips"));
    }

    @Test
    void theHttpInterfaceMakesTheSameCalls() throws Exception {
        long id = create("Webcam", "59");

        mockMvc.perform(get("/api/products/" + id + "/via-http-interface"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Webcam"));
        // No call-specific 404 handling there: the default status handler maps it to a remote error (502).
        mockMvc.perform(get("/api/products/999999/via-http-interface"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.remoteStatus").value(404));
    }

    @Test
    void theHttpInterfaceSupportsEveryVerb(@Autowired ProductHttpApi api) {
        var created = api.create(new ProductRequest("Chair", new BigDecimal("80")), uniqueKey());
        ProductDto product = created.getBody();

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(api.list("chair")).extracting(ProductDto::id).contains(product.id());
        assertThat(api.patch(product.id(), new ProductPatchRequest("Armchair", null)).name()).isEqualTo("Armchair");
        assertThat(api.replace(product.id(), new ProductRequest("Sofa", BigDecimal.TEN)).price())
                .isEqualByComparingTo("10");
        api.delete(product.id());
        assertThat(api.list("sofa")).extracting(ProductDto::id).doesNotContain(product.id());
    }
}
