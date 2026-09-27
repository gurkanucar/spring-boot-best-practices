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
import com.gucardev.logtobasicsecurity.security.SecurityConfig;
import com.gucardev.logtobasicsecurity.security.TestLogtoRegistration;
import com.gucardev.logtobasicsecurity.user.AppUserService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The security rules, without Logto: {@code oidcLogin()} puts a signed-in OIDC user into the request,
 * {@link TestLogtoRegistration} replaces the Logto registration from application.yaml.
 */
@WebMvcTest({HomeController.class, ReportController.class})
@Import({SecurityConfig.class, LogtoOidcUserService.class, CurrentUserService.class, ReportService.class,
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

        mvc.perform(get("/me").with(oidcLogin().idToken(token -> token
                        .subject("logto-user-1")
                        .claim("email", "alice@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logtoId").value("logto-user-1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.claims.email").value("alice@example.com"));
    }

    @Test
    void adminNeedsTheAdminRole() throws Exception {
        mvc.perform(get("/admin").with(oidcLogin()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/admin").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isOk());
    }

    @Test
    void userPageNeedsTheUserRole() throws Exception {
        mvc.perform(get("/user").with(oidcLogin()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/user").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isOk());
    }

    @Test
    void adminIncludesUserButNotTheOtherWayRound() throws Exception {
        // RoleHierarchy: admin implies user, for @PreAuthorize ...
        mvc.perform(get("/user").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isOk());

        // ... but user does not imply admin, and the URL rule for /admin still needs admin.
        mvc.perform(get("/admin").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportsNeedAnyOfTheRoles() throws Exception {
        mvc.perform(get("/reports").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isOk());
        mvc.perform(get("/reports").with(oidcLogin()
                        .idToken(token -> token.subject("logto-user-2"))
                        .authorities(new SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isOk())
                // ReportService read the user from CurrentUserService, not from a parameter.
                .andExpect(jsonPath("$.requestedBy").value("logto-user-2"));

        mvc.perform(get("/reports").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_editor"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void signOutAlsoEndsTheSessionAtLogto() throws Exception {
        var logto = clientRegistrations.findByRegistrationId("logto");

        mvc.perform(post("/logout").with(oidcLogin().clientRegistration(logto)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", allOf(
                        startsWith("http://logto.test/oidc/session/end?id_token_hint="),
                        containsString("post_logout_redirect_uri=http://localhost/"))));
    }
}
