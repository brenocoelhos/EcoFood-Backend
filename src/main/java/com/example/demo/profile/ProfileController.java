package com.example.demo.profile;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final com.example.demo.auth.UsernameRepository usernames;
    public ProfileController(com.example.demo.auth.UsernameRepository usernames) { this.usernames = usernames; }
    @GetMapping
    public AccountResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> metadata = jwt.getClaimAsMap("user_metadata");
        return new AccountResponse(jwt.getSubject(), jwt.getClaimAsString("email"),
                text(metadata, "name"), text(metadata, "last_name"), usernames.usernameFor(java.util.UUID.fromString(jwt.getSubject())));
    }

    private String text(Map<String, Object> metadata, String key) {
        return metadata != null && metadata.get(key) instanceof String value ? value : null;
    }

    // User-editable metadata is display data, never authorization data.
    public record AccountResponse(String id, String email, String name, String lastName, String username) {}
}
