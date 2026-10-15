package com.example.demo.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTests {
    @Test void limitsEachPeerAndRecoversAfterFiveMinutes() {
        var clock = new MutableClock();
        var limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < 20; i++) limiter.check("peer-one");
        assertEquals(429, assertThrows(ResponseStatusException.class,
                () -> limiter.check("peer-one")).getStatusCode().value());
        assertDoesNotThrow(() -> limiter.check("peer-two"));
        clock.now = clock.now.plusSeconds(301);
        assertDoesNotThrow(() -> limiter.check("peer-one"));
    }
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-11T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
