package com.example.demo;
import com.example.demo.admin.TrustedIdentityClient;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.mockito.Mockito.*;

/** Only tests mock the remote identity call; production has no bypass or test profile switch. */
abstract class VerifiedIdentityTestSupport {
    @MockitoBean TrustedIdentityClient identities;
    @BeforeEach void verifiedIdentity() {
        when(identities.verify(any())).thenAnswer(invocation->{
            Jwt jwt=invocation.getArgument(0);
            return new TrustedIdentityClient.Identity(UUID.fromString(jwt.getSubject()),jwt.getClaimAsString("email"),Instant.parse("2026-01-01T00:00:00Z"));
        });
    }
}
