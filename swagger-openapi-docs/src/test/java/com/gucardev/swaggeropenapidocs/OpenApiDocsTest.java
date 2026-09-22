package com.gucardev.swaggeropenapidocs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// springdoc builds the document at runtime, so a broken annotation fails here rather than
// at compile time - and only here does anything actually replay the documented examples.
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
class OpenApiDocsTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void apiDocsDescribeEveryEndpoint() {
        JsonNode paths = apiDocs().get("paths");

        assertThat(apiDocs().at("/info/title").asString()).isEqualTo("swagger-openapi-docs API");
        for (String path : List.of("/api/products", "/api/products/{id}", "/api/products/slow", "/api/products/exception")) {
            assertThat(paths.has(path)).as("documented path %s", path).isTrue();
        }
        assertThat(paths.at("/~1api~1products/post")).isNotEmpty();
    }

    @Test
    void schemasCarryExampleValues() {
        JsonNode schemas = apiDocs().get("components").get("schemas");

        assertThat(schemas.at("/ProductRequest/properties/sku/example").asString()).isEqualTo("SKU-0042");
        assertThat(schemas.at("/ProductRequest/properties/price/example").asDouble()).isEqualTo(1499.90);
        assertThat(schemas.at("/ProductResponse/properties/id/example").asInt()).isEqualTo(1);
        // The envelope is generated too, which is what keeps the doc matching the wire.
        assertThat(schemas.has("ApiResultProductResponse")).isTrue();
        assertThat(schemas.has("PageInfo")).isTrue();
    }

    @Test
    void documentedResponseCodesSurviveTheAnnotations() {
        JsonNode post = apiDocs().at("/paths/~1api~1products/post/responses");
        assertThat(post.has("201")).isTrue();
        assertThat(post.has("409")).isTrue();
        assertThat(apiDocs().at("/paths/~1api~1products~1{id}/get/responses").has("404")).isTrue();
    }

    // The point of these two: annotation-declared documentation is just strings until
    // something replays it. This is the cheap half of what Spring REST Docs gives you -
    // the document cannot claim a parameter or an example that the app rejects.
    @Test
    void documentedQueryParametersAreActuallyAccepted() {
        JsonNode params = apiDocs().at("/paths/~1api~1products/get/parameters");
        assertThat(params).isNotEmpty();

        StringBuilder query = new StringBuilder("/api/products?");
        for (JsonNode param : params) {
            String name = param.get("name").asString();
            String value = switch (name) {
                case "page" -> "0";
                case "size" -> "5";
                default -> "";
            };
            query.append(name).append('=').append(value).append('&');
        }

        client.get().uri(query.toString()).exchange().expectStatus().isOk();
    }

    // @Hidden removes the whole operation from the doc (unlike springdoc.api-docs.enabled=false,
    // which is a profile-wide switch), but the route itself is still live.
    @Test
    void hiddenOperationIsAbsentFromTheDocButStillCallable() {
        assertThat(apiDocs().get("paths").has("/api/products/internal/reset")).isFalse();

        client.post()
                .uri("/api/products/internal/reset")
                .exchange()
                .expectStatus()
                .isOk();
    }

    // @Parameter(hidden = true) removes just this one parameter from the doc while the
    // endpoint still accepts it on the wire - unlike the page/size pair, which is meant to
    // be public and shows up in documentedQueryParametersAreActuallyAccepted() above.
    @Test
    void hiddenParameterIsAbsentFromTheDocButStillAccepted() {
        JsonNode params = apiDocs().at("/paths/~1api~1products/get/parameters");
        List<String> names = new ArrayList<>();
        params.forEach(p -> names.add(p.get("name").asString()));
        assertThat(names).containsExactlyInAnyOrder("page", "size");

        client.get()
                .uri("/api/products")
                .header("X-Internal-Client", "batch-job-42")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void documentedRequestExamplesProduceDocumentedStatuses() {
        JsonNode post = apiDocs().at("/paths/~1api~1products/post");
        List<String> documented = new ArrayList<>();
        post.get("responses").propertyNames().forEach(documented::add);
        assertThat(documented).isNotEmpty();

        JsonNode examples = post.at("/requestBody/content/application~1json/examples");
        assertThat(examples).isNotEmpty();

        for (JsonNode example : examples) {
            JsonNode value = example.get("value");
            String payload = value.isTextual() ? value.asString() : value.toString();

            int status = client.post()
                    .uri("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange()
                    .returnResult()
                    .getStatus()
                    .value();

            assertThat(String.valueOf(status))
                    .as("example %s produced an undocumented status", example.path("summary").asString(""))
                    .isIn(documented);
        }
    }

    private JsonNode apiDocs() {
        byte[] body = client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return objectMapper.readTree(new String(body));
    }
}
