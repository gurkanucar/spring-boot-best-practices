package com.gucardev.restapiintegration.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gucardev.restapiintegration.IntegrationTestBase;
import com.gucardev.restapiintegration.client.error.RemoteApiClientException;
import com.gucardev.restapiintegration.client.error.RemoteApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

/** Basic auth and the API key header, against the protected remote product endpoint. */
class RemoteApiAuthTest extends IntegrationTestBase {

    @Autowired
    private RemoteApiProperties properties;
    @Autowired
    private RestClient.Builder builder;

    private RestClient clientWith(String password, String apiKey) {
        var props = new RemoteApiProperties(properties.baseUrl(), properties.username(), password, apiKey,
                properties.connectTimeout(), properties.readTimeout());
        return new RestClientConfig().remoteApiRestClient(builder, props);
    }

    @Test
    void validCredentialsAndApiKeyAreAccepted() {
        String body = clientWith(properties.password(), properties.apiKey())
                .get().uri("/products").retrieve().body(String.class);

        assertThat(body).contains("Keyboard");
    }

    @Test
    void wrongPasswordIs401AsATypedClientException() {
        assertThatThrownBy(() -> clientWith("wrong", properties.apiKey()).get().uri("/products").retrieve().body(String.class))
                .isInstanceOf(RemoteApiClientException.class)
                .extracting(e -> ((RemoteApiException) e).getStatus()).isEqualTo(401);
    }

    @Test
    void wrongApiKeyIs403() {
        assertThatThrownBy(() -> clientWith(properties.password(), "wrong").get().uri("/products").retrieve().body(String.class))
                .isInstanceOf(RemoteApiClientException.class)
                .extracting(e -> ((RemoteApiException) e).getStatus()).isEqualTo(403);
    }

    @Test
    void errorMessageContainsNoQueryStringOrCredentials() {
        assertThatThrownBy(() -> clientWith("wrong", properties.apiKey())
                .get().uri("/products?name={n}", "secret-search").retrieve().body(String.class))
                .hasMessage("GET /remote-api/products failed with status 401");
    }
}
