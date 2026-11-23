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
    private final org.springframework.jdbc.core.JdbcTemplate db;
    public ProfileController(com.example.demo.auth.UsernameRepository usernames,org.springframework.jdbc.core.JdbcTemplate db) { this.usernames = usernames;this.db=db; }
    @GetMapping
    public AccountResponse getProfile(@AuthenticationPrincipal Jwt jwt,org.springframework.security.core.Authentication authentication,
            jakarta.servlet.http.HttpServletRequest request) {
        Map<String, Object> metadata = jwt.getClaimAsMap("user_metadata");
        var identity=(com.example.demo.admin.TrustedIdentityClient.Identity)request.getAttribute("verifiedIdentity");
        var id=java.util.UUID.fromString(jwt.getSubject());
        String type=authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"))?"admin":
            db.queryForObject("SELECT count(*) FROM ecofood.markets WHERE owner_id=?",Integer.class,id)>0?"marketOwner":null;
        return new AccountResponse(jwt.getSubject(), identity.email(),
                text(metadata, "name"), text(metadata, "last_name"), usernames.usernameFor(id),type);
    }

    private String text(Map<String, Object> metadata, String key) {
        return metadata != null && metadata.get(key) instanceof String value ? value : null;
    }

    // User-editable metadata is display data, never authorization data.
    public record AccountResponse(String id, String email, String name, String lastName, String username,String accountType) {}
}
