package com.gucardev.restapiintegration.remote;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * A tiny HTTPS server with a SELF-SIGNED certificate, standing in for a partner or internal
 * service whose certificate is not signed by a public CA. The SSL examples call it.
 * {@code https://localhost:8443/hello}
 */
@Component
public class DemoHttpsServer implements SmartLifecycle {

    private final SslBundles sslBundles;
    private final int configuredPort;
    private HttpsServer server;

    public DemoHttpsServer(SslBundles sslBundles, MockRemoteProperties properties) {
        this.sslBundles = sslBundles;
        this.configuredPort = properties.httpsPort();
    }

    @Override
    public void start() {
        try {
            server = HttpsServer.create(new InetSocketAddress("localhost", configuredPort), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(sslBundles.getBundle("demo-server").createSslContext()));
            server.createContext("/hello", exchange -> {
                byte[] body = "{\"message\":\"hello over TLS\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the demo HTTPS server", e);
        }
    }

    @Override
    public void stop() {
        server.stop(0);
        server = null;
    }

    @Override
    public boolean isRunning() {
        return server != null;
    }

    /** The actual port; differs from the configured one when that is 0 (tests). */
    public String helloUrl() {
        return "https://localhost:" + server.getAddress().getPort() + "/hello";
    }
}
