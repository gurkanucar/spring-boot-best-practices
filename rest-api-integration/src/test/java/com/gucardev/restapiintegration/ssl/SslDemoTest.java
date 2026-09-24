package com.gucardev.restapiintegration.ssl;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.restapiintegration.IntegrationTestBase;
import org.junit.jupiter.api.Test;

/** Three clients against the same HTTPS server with a self-signed certificate. */
class SslDemoTest extends IntegrationTestBase {

    @Test
    void defaultTrustStoreRejectsTheSelfSignedCertificate() throws Exception {
        mockMvc.perform(get("/api/demo/ssl/default"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.cause").value("SunCertPathBuilderException"));
    }

    @Test
    void sslBundleTrustingThatCertificateWorks() throws Exception {
        mockMvc.perform(get("/api/demo/ssl/trusted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("hello over TLS"));
    }

    @Test
    void disabledVerificationAlsoWorksButAcceptsAnything() throws Exception {
        mockMvc.perform(get("/api/demo/ssl/insecure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("hello over TLS"));
    }

    @Test
    void unknownModeIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/demo/ssl/whatever")).andExpect(status().isBadRequest());
    }
}
