package com.gucardev.logtobasicsecurity.home;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.logtobasicsecurity.report.ReportController;
import com.gucardev.logtobasicsecurity.report.ReportService;
import com.gucardev.logtobasicsecurity.security.CurrentUserService;
import com.gucardev.logtobasicsecurity.security.LogtoOidcUserService;
import com.gucardev.logtobasicsecurity.security.AuthService;
import com.gucardev.logtobasicsecurity.security.AuthUser;
import com.gucardev.logtobasicsecurity.security.SecurityConfig;
import com.gucardev.logtobasicsecurity.security.TestAuthUser;
import com.gucardev.logtobasicsecurity.security.TestLogtoRegistration;
import com.gucardev.logtobasicsecurity.user.AppUserService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The security rules, without Logto: {@code oidcLogin()} puts a signed-in OIDC user into the request,
 * {@link TestLogtoRegistration} replaces the Logto registration from application.yaml.
 */
@WebMvcTest({HomeController.class, ReportController.class})
@Import({SecurityConfig.class, LogtoOidcUserService.class, CurrentUserService.class, ReportService.class, AuthService.class,
        TestLogtoRegistration.class})
class HomeControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserService appUserService;

    @Autowired
    ClientRegistrationRepository clientRegistrations;

    @Test
    void homeIsPublic() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedIn").value(false));
    }

    @Test
    void protectedPageSendsAnonymousUsersToLogto() throws Exception {
        mvc.perform(get("/me"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/logto"));

        // ... which redirects to Logto's authorization endpoint.
        mvc.perform(get("/oauth2/authorization/logto"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith(
                        "http://logto.test/oidc/auth?response_type=code&client_id=test-client")));
    }

    @Test
    void meShowsTheSignedInUser() throws Exception {
        when(appUserService.findByLogtoId("logto-user-1")).thenReturn(Optional.empty());

        mvc.perform(get("/me").with(login(Map.of("sub", "logto-user-1", "email", "alice@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("logto-user-1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.claims.email").value("alice@example.com"));
    }

    @Test
    void adminNeedsTheAdminRole() throws Exception {
        mvc.perform(get("/admin").with(login()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/admin").with(login("admin")))
                .andExpect(status().isOk());
    }

    @Test
    void userPageNeedsTheUserRole() throws Exception {
        mvc.perform(get("/user").with(login()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/user").with(login("user")))
                .andExpect(status().isOk());
    }

    @Test
    void adminIncludesUserButNotTheOtherWayRound() throws Exception {
        // RoleHierarchy: admin implies user, for @PreAuthorize ...
        mvc.perform(get("/user").with(login("admin")))
                .andExpect(status().isOk());

        // ... but user does not imply admin, and the URL rule for /admin still needs admin.
        mvc.perform(get("/admin").with(login("user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportsNeedAnyOfTheRoles() throws Exception {
        mvc.perform(get("/reports").with(login("admin")))
                .andExpect(status().isOk());
        mvc.perform(get("/reports").with(loginAs("logto-user-2", "user")))
                .andExpect(status().isOk())
                // ReportService read the user from CurrentUserService, not from a parameter.
                .andExpect(jsonPath("$.requestedBy").value("logto-user-2"));

        mvc.perform(get("/reports").with(login("editor")))
                .andExpect(status().isForbidden());
    }

    @Test
    void signOutAlsoEndsTheSessionAtLogto() throws Exception {
        var logto = clientRegistrations.findByRegistrationId("logto");

        mvc.perform(post("/logout").with(login().clientRegistration(logto)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", allOf(
                        startsWith("http://logto.test/oidc/session/end?id_token_hint="),
                        containsString("post_logout_redirect_uri=http://localhost/"))));
    }

    /** A signed-in AuthUser, as the OIDC user service would build it: the roles are parsed already. */
    private static OidcLoginRequestPostProcessor login(String... roles) {
        return login(Map.of("sub", "logto-user-1"), roles);
    }

    private static OidcLoginRequestPostProcessor loginAs(String id, String role) {
        return login(Map.of("sub", id), role);
    }

    private static OidcLoginRequestPostProcessor login(Map<String, Object> claims, String... roles) {
        AuthUser user = TestAuthUser.of(claims, roles);
        return oidcLogin().oidcUser(user);
    }
}
