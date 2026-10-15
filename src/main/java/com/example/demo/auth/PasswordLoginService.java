package com.example.demo.auth;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordLoginService {
    private final UsernameRepository usernames;
    private final RestClient client;
    private final String publishableKey;

    public PasswordLoginService(UsernameRepository usernames,
            @Value("${supabase.url}") String url,
            @Value("${supabase.publishable-key:}") String publishableKey) {
        this.usernames = usernames;
        this.publishableKey = publishableKey;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().baseUrl(url + "/auth/v1")
                .requestFactory(factory).build();
    }

    public Tokens login(String identifier, String password) {
        if (publishableKey.isBlank()) throw unavailable();
        String normalized = identifier.strip().toLowerCase(Locale.ROOT);
        String email = normalized.contains("@") ? normalized : usernames.emailFor(normalized);
        // Unknown handles also take the Supabase password path and get the same public error.
        if (email == null) email = "unknown-" + java.util.UUID.randomUUID() + "@invalid.example";
        try {
            return client.post().uri("/token?grant_type=password")
                    .header("apikey", publishableKey).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", password))
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            Tokens tokens = response.bodyTo(Tokens.class);
                            if (tokens == null || tokens.access_token() == null || tokens.refresh_token() == null)
                                throw unavailable();
                            return tokens;
                        }
                        if (response.getStatusCode().value() == 429)
                            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                                    "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
                        if (response.getStatusCode().is5xxServerError()) throw unavailable();
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                "E-mail, usuário ou senha incorretos. Se ainda não confirmou seu e-mail, confirme antes de entrar.");
                    });
        } catch (RestClientException exception) { throw unavailable(); }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "O login está temporariamente indisponível. Tente novamente em instantes.");
    }
    public record Tokens(String access_token, String refresh_token) {}
}
