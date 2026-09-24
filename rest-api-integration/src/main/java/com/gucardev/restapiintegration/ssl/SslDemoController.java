package com.gucardev.restapiintegration.ssl;

import com.gucardev.restapiintegration.remote.DemoHttpsServer;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Calls an HTTPS server with a self-signed certificate ({@link DemoHttpsServer}) three ways:
 * <ul>
 *   <li>{@code default}: the JDK trust store; the handshake is rejected (502)</li>
 *   <li>{@code trusted}: an SSL bundle that trusts exactly this certificate; works, fully verified</li>
 *   <li>{@code insecure}: verification disabled; works, but would also accept an attacker's certificate</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/demo/ssl")
public class SslDemoController {

    public enum Mode { DEFAULT, TRUSTED, INSECURE }

    private final DemoHttpsServer server;
    private final Map<Mode, RestClient> clients;

    public SslDemoController(DemoHttpsServer server,
                             @Qualifier("defaultSsl") RestClient defaultClient,
                             @Qualifier("trustedSsl") RestClient trustedClient,
                             @Qualifier("insecureSsl") RestClient insecureClient) {
        this.server = server;
        this.clients = Map.of(Mode.DEFAULT, defaultClient, Mode.TRUSTED, trustedClient, Mode.INSECURE, insecureClient);
    }

    @GetMapping("/{mode}")
    public Map<String, Object> call(@PathVariable String mode) {
        RestClient client = clients.get(Mode.valueOf(mode.toUpperCase(Locale.ROOT)));
        return client.get().uri(server.helloUrl()).retrieve().body(new ParameterizedTypeReference<>() {
        });
    }
}
