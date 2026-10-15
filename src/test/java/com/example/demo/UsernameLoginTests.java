package com.example.demo;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UsernameLoginTests {
    static final AtomicReference<String> forwarded = new AtomicReference<>();
    static final AtomicReference<String> apiKey = new AtomicReference<>();
    static final HttpServer auth;
    static {
        try {
            auth = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            auth.createContext("/auth/v1/token", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                forwarded.set(body);
                apiKey.set(exchange.getRequestHeaders().getFirst("apikey"));
                boolean correct = body.contains("known@example.com") && body.contains("correct-password");
                int status = body.contains("rate-limited") ? 429 : body.contains("upstream-failed") ? 503 : correct ? 200 : 400;
                String result = correct ? "{\"access_token\":\"test-access\",\"refresh_token\":\"test-refresh\",\"expires_in\":3600,\"user\":{\"email\":\"known@example.com\"}}"
                        : "{\"error_code\":\"invalid_credentials\",\"msg\":\"private provider detail\"}";
                byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            auth.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("supabase.url", () -> "http://127.0.0.1:" + auth.getAddress().getPort());
        registry.add("supabase.publishable-key", () -> "test-public-key");
    }
    @AfterAll static void stop() { auth.stop(0); }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void seed() {
        jdbc.update("insert into ecofood.user_logins(user_id, username, email) values (?, ?, ?)",
                UUID.randomUUID(), "breno_test", "known@example.com");
        forwarded.set(null);
    }

    @Test void usernameResolvesPrivatelyAndReturnsOnlySessionTokens() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"  BrEnO_Test  \",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.access_token").value("test-access"))
                .andExpect(jsonPath("$.refresh_token").value("test-refresh"))
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.user").doesNotExist());
        assertTrue(forwarded.get().contains("known@example.com"));
        assertEquals("test-public-key", apiKey.get());
    }
    @Test void emailStillWorksWithoutUsernameLookup() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\" KNOWN@EXAMPLE.COM \",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").value("test-access"));
    }
    @Test void UnknownUsernameAndWrongPasswordHaveIdenticalPublicErrors() throws Exception {
        String previous = null;
        for (String identifier : new String[]{"does_not_exist", "breno_test", "known@example.com"}) {
            String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"identifier\":\"" + identifier + "\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
            assertFalse(response.contains("known@example.com"));
            assertFalse(response.contains("private provider"));
            if (previous != null) assertEquals(previous, response);
            previous = response;
        }
    }
    @Test void providerRateLimitAndOutageAreNotBadPasswordErrors() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"breno_test\",\"password\":\"rate-limited\"}"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"breno_test\",\"password\":\"upstream-failed\"}"))
                .andExpect(status().isServiceUnavailable());
    }
    @Test void usernameAvailabilityIsNormalizedAndValidated() throws Exception {
        mvc.perform(post("/api/auth/username-availability").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"BRENO_TEST\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(post("/api/auth/username-availability").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"new_user\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        mvc.perform(post("/api/auth/username-availability").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"email@example.com\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void invalidInputNeverCallsProvider() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        assertNull(forwarded.get());
    }
}
