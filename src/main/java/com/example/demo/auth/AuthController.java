package com.example.demo.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.Locale;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final PasswordLoginService login;
    private final UsernameRepository usernames;
    private final LoginRateLimiter limiter;

    public AuthController(PasswordLoginService login, UsernameRepository usernames, LoginRateLimiter limiter) {
        this.login = login;
        this.usernames = usernames;
        this.limiter = limiter;
    }

    @PostMapping("/login")
    public ResponseEntity<PasswordLoginService.Tokens> login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request) {
        limiter.check(request.getRemoteAddr());
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("Pragma", "no-cache")
                .body(login.login(body.identifier(), body.password()));
    }

    @PostMapping("/username-availability")
    public Map<String, Boolean> available(@Valid @RequestBody UsernameRequest body, HttpServletRequest request) {
        limiter.check(request.getRemoteAddr());
        String username = body.username().strip().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9_.]{3,30}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use de 3 a 30 caracteres: letras sem acento, números, ponto ou sublinhado.");
        return Map.of("available", usernames.available(username));
    }

    public record LoginRequest(@NotBlank @Size(max = 320) String identifier,
                               @NotBlank @Size(max = 4096) String password) {
        @Override public String toString() { return "LoginRequest[redacted]"; }
    }
    public record UsernameRequest(@NotBlank @Size(max = 30) String username) {}

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> authenticate() {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "code", "AUTH_MOVED_TO_SUPABASE",
                "message", "Use Supabase Auth in the app and send its access token as a Bearer token."));
    }
}
