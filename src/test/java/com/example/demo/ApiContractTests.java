package com.example.demo;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiContractTests extends VerifiedIdentityTestSupport {
    private static final String ISSUER = "https://test.supabase.co/auth/v1";
    private static final String USER_ID = "d1a790af-14c7-4f74-a558-a951d143aa22";
    private static final ECKey KEY;
    private static final HttpServer JWKS_SERVER;
    static {
        try {
            KEY = new ECKeyGenerator(Curve.P_256).keyID("test-key").generate();
            JWKS_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS_SERVER.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            JWKS_SERVER.start();
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void stopJwksServer() { JWKS_SERVER.stop(0); }

    @Autowired private MockMvc mockMvc;

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer(ISSUER).subject(USER_ID)
                .audience("authenticated").claim("role", "authenticated")
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("email", "user@example.com")
                .claim("user_metadata", Map.of("name", "Ana", "last_name", "Silva", "username", "ana"));
    }

    private String token(JWTClaimsSet.Builder claims, ECKey key) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(KEY.getKeyID()).build(), claims.build());
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }

    @Test void demoAuthenticationIsGone() throws Exception {
        for (String path : new String[]{"/api/auth/register"}) {
            mockMvc.perform(post(path)).andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value("AUTH_MOVED_TO_SUPABASE"))
                    .andExpect(jsonPath("$.token").doesNotExist());
        }
    }

    @Test void profileRequiresToken() throws Exception {
        mockMvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
    }

    @Test void demoTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer dev-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test void validSignedTokenReturnsItsOwnUser() throws Exception {
        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + token(claims(), KEY)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.lastName").value("Silva"));
    }

    @Test void missingMetadataDoesNotBreakProfile() throws Exception {
        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + token(claims().claim("user_metadata", null), KEY)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(USER_ID));
    }

    @Test void tokenWithDifferentSignatureIsRejected() throws Exception {
        ECKey attackerKey = new ECKeyGenerator(Curve.P_256).generate();
        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + token(claims(), attackerKey)))
                .andExpect(status().isUnauthorized());
    }

    @Test void invalidClaimsAreRejected() throws Exception {
        JWTClaimsSet.Builder[] invalid = {
            claims().issuer("https://other.supabase.co/auth/v1"),
            claims().audience("other"),
            claims().expirationTime(Date.from(Instant.now().minusSeconds(120))),
            claims().expirationTime(null),
            claims().notBeforeTime(Date.from(Instant.now().plusSeconds(600))),
            claims().claim("role", "service_role"),
            claims().subject(null),
            claims().subject("not-a-user-uuid")
        };
        for (var badClaims : invalid) {
            mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + token(badClaims, KEY)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test void otherRoutesAreProtectedByDefault() throws Exception {
        mockMvc.perform(post("/api/markets")).andExpect(status().isUnauthorized());
    }

    @Test void marketsRemainPublic() throws Exception {
        mockMvc.perform(get("/api/markets")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test void productsRemainPublic() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
