package com.example.demo.profile;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class DeleteAccountController {
    private final RestClient client;
    private final String key;
    public DeleteAccountController(@Value("${supabase.url}") String url,
            @Value("${supabase.service-role-key:}") String key) {
        this.key = key;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        client = RestClient.builder().baseUrl(url + "/auth/v1").requestFactory(factory).build();
    }

    @DeleteMapping("/api/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt) {
        if (key.isBlank()) throw unavailable();
        // Only the authenticated identity can be deleted. Database foreign keys
        // cascade auth.users deletion to the market, catalog, images and access data.
        UUID id = UUID.fromString(jwt.getSubject());
        try {
            client.delete().uri("/admin/users/{id}", id).header("apikey", key)
                .headers(headers -> headers.setBearerAuth(key))
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) throw unavailable();
                    return null;
                });
        } catch (RestClientException exception) { throw unavailable(); }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Não foi possível excluir sua conta agora. Tente novamente mais tarde.");
    }
}
