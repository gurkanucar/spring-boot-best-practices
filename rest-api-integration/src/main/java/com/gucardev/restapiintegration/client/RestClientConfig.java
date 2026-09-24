package com.gucardev.restapiintegration.client;

import com.gucardev.restapiintegration.client.error.RemoteApiException;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.autoconfigure.RestClientSsl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * One configured {@link RestClient} per remote system (or per way of calling it).
 *
 * <p>Every bean starts from Spring Boot's auto-configured {@code RestClient.Builder}, which
 * already has the application's JSON mapper and observation support. The builder bean is
 * prototype scoped, so each injection point receives its own copy and customizing it here does
 * not leak into other clients.
 */
@Configuration
public class RestClientConfig {

    /** The main client: base URL, Basic auth, API key header, timeouts, interceptors, error mapping. */
    @Bean
    @Qualifier("remoteApi")
    RestClient remoteApiRestClient(RestClient.Builder builder, RemoteApiProperties properties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                // Explicit JDK HttpClient: supports PATCH (the old HttpURLConnection-based factory does not).
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(properties.username(), properties.password());
                    headers.set("X-Api-Key", properties.apiKey());
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .defaultHeader("User-Agent", "rest-api-integration/1.0")
                .requestInterceptor(new CorrelationIdInterceptor())
                .requestInterceptor(new LoggingInterceptor())
                // Any 4xx/5xx not handled by a call-specific onStatus(...) becomes a typed exception.
                .defaultStatusHandler(HttpStatusCode::isError,
                        (request, response) -> {
                            throw RemoteApiException.from(request, response);
                        })
                .build();
    }

    /** No SSL customization: the JDK default trust store. A self-signed server is rejected. */
    @Bean
    @Qualifier("defaultSsl")
    RestClient defaultSslRestClient(RestClient.Builder builder) {
        return builder.requestFactory(ClientHttpRequestFactoryBuilder.jdk().build()).build();
    }

    /**
     * The right way to call a server with a self-signed or private-CA certificate: trust exactly
     * that certificate through a Spring Boot SSL bundle ({@code spring.ssl.bundle.pem.demo-server-trust}).
     * Hostname verification and every other TLS check stay on.
     */
    @Bean
    @Qualifier("trustedSsl")
    RestClient trustedSslRestClient(RestClient.Builder builder, RestClientSsl ssl) {
        return builder.apply(ssl.fromBundle("demo-server-trust")).build();
    }

    /**
     * SSL verification DISABLED: trusts any certificate from anyone, including an attacker in the
     * middle. For local development against throwaway servers only; never enable in production.
     */
    @Bean
    @Qualifier("insecureSsl")
    RestClient insecureSslRestClient(RestClient.Builder builder) {
        var requestFactory = ClientHttpRequestFactoryBuilder.jdk()
                .withHttpClientCustomizer(httpClient -> httpClient.sslContext(InsecureSsl.trustAllContext()))
                .build();
        return builder.requestFactory(requestFactory).build();
    }
}
