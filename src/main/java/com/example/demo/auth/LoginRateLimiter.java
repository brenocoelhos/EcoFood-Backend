package com.example.demo.auth;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Bounded, per-instance limit. Uses the socket peer, never untrusted forwarded headers. */
@Component
public class LoginRateLimiter {
    private final Clock clock;
    private final Map<String, Bucket> buckets = new HashMap<>();
    public LoginRateLimiter() { this(Clock.systemUTC()); }
    LoginRateLimiter(Clock clock) { this.clock = clock; }

    public synchronized void check(String address) {
        long now = clock.millis();
        buckets.values().removeIf(bucket -> bucket.expiresAt <= now);
        Bucket bucket = buckets.get(address);
        if (bucket == null) {
            if (buckets.size() >= 10_000) throw limited();
            bucket = new Bucket(now + 300_000);
            buckets.put(address, bucket);
        }
        if (++bucket.attempts > 20) throw limited();
    }

    private ResponseStatusException limited() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas. Aguarde cinco minutos antes de tentar novamente.");
    }
    private static class Bucket {
        final long expiresAt;
        int attempts;
        Bucket(long expiresAt) { this.expiresAt = expiresAt; }
    }
}
