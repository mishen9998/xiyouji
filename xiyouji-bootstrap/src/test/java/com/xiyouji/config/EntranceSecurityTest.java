package com.xiyouji.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyouji.controller.AuthController;
import com.xiyouji.security.JwtAuthenticationFilter;
import com.xiyouji.security.JwtUtil;
import com.xiyouji.service.AuthService;
import com.xiyouji.service.CommandIdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(EntranceSecurityTest.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = "app.security.enforce-jwt=true")
class EntranceSecurityTest {
    @Autowired WebApplicationContext context;
    @Autowired JwtUtil jwt;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(jwt);
        when(jwt.validateToken("valid-guest")).thenReturn(true);
        when(jwt.extractUsername("valid-guest")).thenReturn("guest_entrance");
        when(jwt.extractRole("valid-guest")).thenReturn("GUEST");
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void invalidOrMissingTokenIsUnauthorizedNotForbidden() throws Exception {
        for (String token : new String[]{"", "Bearer invalid-or-expired"}) {
            mvc.perform(post("/api/game/new").header("Authorization", token)
                    .header("X-Idempotency-Key", "entrance-test"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
    }

    @Test void compressedIllustrationsAndSpaEntranceRoutesArePublic() throws Exception {
        for (String path : new String[]{"/illustrations/portrait.webp", "/illustrations/portrait.avif", "/menu", "/complete"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test void sessionProbeRequiresAValidTokenDespitePublicAuthPrefix() throws Exception {
        mvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/session").header("Authorization", "Bearer invalid-or-expired"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/session").header("Authorization", "Bearer valid-guest"))
            .andExpect(status().isNoContent());
    }

    @Test void permissionDenialRemainsForbiddenForAuthenticatedGuest() throws Exception {
        mvc.perform(get("/api/test/admin").header("Authorization", "Bearer valid-guest"))
            .andExpect(status().isForbidden());
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, StrictCommandHeadersFilter.class, AuthController.class, PublicFixtures.class})
    static class TestConfig {
        @Bean JwtUtil jwtUtil() { return mock(JwtUtil.class); }
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean CommandIdempotencyService idempotency() { return mock(CommandIdempotencyService.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    @RestController
    static class PublicFixtures {
        @GetMapping({"/illustrations/portrait.webp", "/illustrations/portrait.avif", "/menu", "/complete"})
        String publicResource() { return "public"; }
        @GetMapping("/api/test/admin") @PreAuthorize("hasRole('ADMIN')")
        String adminOnly() { return "admin"; }
    }
}
