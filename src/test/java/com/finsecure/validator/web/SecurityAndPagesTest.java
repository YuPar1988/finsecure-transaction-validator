package com.finsecure.validator.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.admin.username=ops", "app.admin.password=Test-Only-Pass-1"})
@AutoConfigureMockMvc
class SecurityAndPagesTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void dashboardRequiresLogin() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void logsRequireLogin() throws Exception {
        mvc.perform(get("/logs")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/dashboard/data")).andExpect(status().is3xxRedirection());
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Sign in")));
    }

    @Test
    void adminCanSignInWithConfiguredPassword() throws Exception {
        mvc.perform(formLogin("/login").user("ops").password("Test-Only-Pass-1"))
                .andExpect(authenticated().withUsername("ops"))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void wrongPasswordIsRefused() throws Exception {
        mvc.perform(formLogin("/login").user("ops").password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void validateDoesNotNeedCsrfToken() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void signedInAdminCanOpenAllPages() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isOk());
        mvc.perform(get("/dashboard").param("window", "1h")).andExpect(status().isOk());
        mvc.perform(get("/dashboard").param("window", "7d")).andExpect(status().isOk());
        mvc.perform(get("/dashboard/data")).andExpect(status().isOk());
        mvc.perform(get("/logs")).andExpect(status().isOk());
        mvc.perform(get("/logs").param("statusGroup", "4xx").param("method", "POST")).andExpect(status().isOk());
        mvc.perform(get("/logs/export")).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString("api-request-log.csv")));
        mvc.perform(get("/monitoring")).andExpect(status().isOk());
        mvc.perform(get("/monitoring/data")).andExpect(status().isOk());
        mvc.perform(get("/tester")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownLogEntryReturns404() throws Exception {
        mvc.perform(get("/logs/999999")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void logoutRedirectsToLogin() throws Exception {
        mvc.perform(post("/logout").with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
