package com.gucardev.logtobasicsecurity.api;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.logtobasicsecurity.security.ApiSecurityConfig;
import com.gucardev.logtobasicsecurity.security.LogtoOidcUserService;
import com.gucardev.logtobasicsecurity.security.SecurityConfig;
import com.gucardev.logtobasicsecurity.security.TestLogtoRegistration;
import com.gucardev.logtobasicsecurity.user.AppUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The bearer-token API rules, without Logto: {@code jwt()} puts an already validated access token into
 * the request, so no key or network is needed.
 */
@WebMvcTest(ApiController.class)
@Import({ApiSecurityConfig.class, SecurityConfig.class, LogtoOidcUserService.class, TestLogtoRegistration.class})
class ApiControllerTest {

    private static final SimpleGrantedAuthority READ = new SimpleGrantedAuthority("SCOPE_read:reports");
    private static final SimpleGrantedAuthority WRITE = new SimpleGrantedAuthority("SCOPE_write:reports");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserService appUserService;

    @Test
    void noTokenIs401NotARedirectToLogto() throws Exception {
        // An API client cannot follow a redirect to a sign-in page: it must get 401 + WWW-Authenticate.
        mvc.perform(get("/api/whoami"))
                .andExpect(status().isUnauthorized())
                // Spring Security 7 also adds resource_metadata (RFC 9728, OAuth protected resource metadata).
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
    }

    @Test
    void whoamiShowsWhoTheTokenWasIssuedTo() throws Exception {
        mvc.perform(get("/api/whoami").with(jwt()
                        .jwt(token -> token.subject("api-demo-client").claim("client_id", "api-demo-client"))
                        .authorities(READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("api-demo-client"))
                .andExpect(jsonPath("$.clientId").value("api-demo-client"))
                .andExpect(jsonPath("$.authorities[0]").value("SCOPE_read:reports"));
    }

    @Test
    void readingReportsNeedsTheReadScope() throws Exception {
        mvc.perform(get("/api/reports").with(jwt().authorities(READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").exists());

        mvc.perform(get("/api/reports").with(jwt().authorities(WRITE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingReportsNeedsTheWriteScopeButNoCsrfToken() throws Exception {
        // Stateless bearer-token API: no session cookie, so no CSRF token either.
        mvc.perform(post("/api/reports").with(jwt().authorities(READ))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Q3\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/reports").with(jwt().authorities(READ, WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Q3\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Q3"));
    }

    @Test
    void theWebPartStillUsesTheLogtoSignIn() throws Exception {
        // The API chain only matches /api/**; everything else keeps redirecting to Logto.
        mvc.perform(get("/me"))
                .andExpect(status().is3xxRedirection());
    }
}
