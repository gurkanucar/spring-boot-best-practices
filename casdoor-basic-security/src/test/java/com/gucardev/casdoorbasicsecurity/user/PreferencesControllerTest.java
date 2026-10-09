package com.gucardev.casdoorbasicsecurity.user;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.casdoorbasicsecurity.security.CasdoorOidcUserService;
import com.gucardev.casdoorbasicsecurity.security.SecurityConfig;
import com.gucardev.casdoorbasicsecurity.security.TestAuthUser;
import com.gucardev.casdoorbasicsecurity.security.TestCasdoorRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PreferencesController.class)
@Import({SecurityConfig.class, CasdoorOidcUserService.class, TestCasdoorRegistration.class})
class PreferencesControllerTest {

    private static final String BODY = """
            {"nickname": "Ally", "theme": "dark"}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserService appUserService;

    @Test
    void savesThePreferencesOfTheSignedInUser() throws Exception {
        mvc.perform(put("/me/preferences").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(oidcLogin().oidcUser(TestAuthUser.of("casdoor-user-1"))).with(csrf()))
                .andExpect(status().isNoContent());

        verify(appUserService).updatePreferences("casdoor-user-1", "Ally", "dark");
    }

    @Test
    void needsTheCsrfToken() throws Exception {
        mvc.perform(put("/me/preferences").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(oidcLogin().oidcUser(TestAuthUser.of("casdoor-user-1"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUsersAreSentToSignIn() throws Exception {
        mvc.perform(put("/me/preferences").contentType(MediaType.APPLICATION_JSON).content(BODY).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}
