package com.example.demo.admin;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TrustedIdentityClient {
    private final RestClient client;
    private final String key;
    public TrustedIdentityClient(@Value("${supabase.url}") String url,@Value("${supabase.publishable-key:}") String key) {
        this.key=key;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        client=RestClient.builder().baseUrl(url+"/auth/v1").requestFactory(factory).build();
    }
    public Identity verify(Jwt jwt) {
        if(key.isBlank()) throw unavailable();
        try {
            return client.get().uri("/user").header("apikey",key).headers(h->h.setBearerAuth(jwt.getTokenValue()))
                .exchange((req,res)->{
                    if(res.getStatusCode().value()==401 || res.getStatusCode().value()==403)
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sessão inválida.");
                    if(!res.getStatusCode().is2xxSuccessful()) throw unavailable();
                    var identity=res.bodyTo(Identity.class);
                    if(identity==null || identity.id()==null || !identity.id().toString().equals(jwt.getSubject()))
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sessão inválida.");
                    return identity;
                });
        } catch(RestClientException e) {throw unavailable();}
    }
    private ResponseStatusException unavailable() {return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Não foi possível verificar a sessão. Tente novamente.");}
    public record Identity(UUID id,String email,Instant email_confirmed_at) {
        @Override public String toString() {return "Identity[redacted]";}
    }
}
