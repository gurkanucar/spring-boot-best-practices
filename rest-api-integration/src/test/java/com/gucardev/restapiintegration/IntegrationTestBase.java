package com.gucardev.restapiintegration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Starts the application on a real, free port, because the clients under test make real HTTP
 * calls to the simulated remote API ({@code remote-api.base-url} uses {@code server.port}).
 * Our own endpoints are driven with MockMvc. All subclasses share one application context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        int port = freePort();
        registry.add("server.port", () -> port);
        registry.add("mock-remote.https-port", () -> 0);
        registry.add("remote-api.read-timeout", () -> "1s");
    }

    /** Remote hit counters are per key, so a fresh key isolates each test. */
    protected static String uniqueKey() {
        return UUID.randomUUID().toString();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
