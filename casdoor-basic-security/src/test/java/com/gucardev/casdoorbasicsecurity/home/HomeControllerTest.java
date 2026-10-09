package com.gucardev.casdoorbasicsecurity.home;

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

import com.gucardev.casdoorbasicsecurity.report.ReportController;
import com.gucardev.casdoorbasicsecurity.report.ReportService;
import com.gucardev.casdoorbasicsecurity.security.CurrentUserService;
import com.gucardev.casdoorbasicsecurity.security.CasdoorOidcUserService;
import com.gucardev.casdoorbasicsecurity.security.AuthService;
import com.gucardev.casdoorbasicsecurity.security.AuthUser;
import com.gucardev.casdoorbasicsecurity.security.SecurityConfig;
import com.gucardev.casdoorbasicsecurity.security.TestAuthUser;
import com.gucardev.casdoorbasicsecurity.security.TestCasdoorRegistration;
import com.gucardev.casdoorbasicsecurity.user.AppUserService;
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
 * The security rules, without Casdoor: {@code oidcLogin()} puts a signed-in OIDC user into the request,
 * {@link TestCasdoorRegistration} replaces the Casdoor registration from application.yaml.
 */
@WebMvcTest({HomeController.class, ReportController.class})
@Import({SecurityConfig.class, CasdoorOidcUserService.class, CurrentUserService.class, ReportService.class, AuthService.class,
        TestCasdoorRegistration.class})
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
    void protectedPageSendsAnonymousUsersToCasdoor() throws Exception {
        mvc.perform(get("/me"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/casdoor"));

        // ... which redirects to Casdoor's authorization endpoint.
        mvc.perform(get("/oauth2/authorization/casdoor"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith(
                        "http://casdoor.test/login/oauth/authorize?response_type=code&client_id=test-client")));
    }

    @Test
    void meShowsTheSignedInUser() throws Exception {
        when(appUserService.findByCasdoorId("casdoor-user-1")).thenReturn(Optional.empty());

        mvc.perform(get("/me").with(login(Map.of("sub", "casdoor-user-1", "email", "alice@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("casdoor-user-1"))
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
        mvc.perform(get("/reports").with(loginAs("casdoor-user-2", "user")))
                .andExpect(status().isOk())
                // ReportService read the user from CurrentUserService, not from a parameter.
                .andExpect(jsonPath("$.requestedBy").value("casdoor-user-2"));

        mvc.perform(get("/reports").with(login("editor")))
                .andExpect(status().isForbidden());
    }

    @Test
    void signOutAlsoEndsTheSessionAtCasdoor() throws Exception {
        var casdoor = clientRegistrations.findByRegistrationId("casdoor");

        mvc.perform(post("/logout").with(login().clientRegistration(casdoor)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", allOf(
                        startsWith("http://casdoor.test/api/logout?id_token_hint="),
                        containsString("post_logout_redirect_uri=http://localhost/"))));
    }

    /** A signed-in AuthUser, as the OIDC user service would build it: the roles are parsed already. */
    private static OidcLoginRequestPostProcessor login(String... roles) {
        return login(Map.of("sub", "casdoor-user-1"), roles);
    }

    private static OidcLoginRequestPostProcessor loginAs(String id, String role) {
        return login(Map.of("sub", id), role);
    }

    private static OidcLoginRequestPostProcessor login(Map<String, Object> claims, String... roles) {
        AuthUser user = TestAuthUser.of(claims, roles);
        return oidcLogin().oidcUser(user);
    }
}
