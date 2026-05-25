package com.example.demo.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return new AuthResponse(
                "dev-token",
                new UserProfileResponse("Breno Coelho", "@brenocoelhos", resolveEmail(request.identifier()), "mar\u00e7o de 2026")
        );
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return new AuthResponse(
                "dev-token",
                new UserProfileResponse(
                        request.name() + " " + request.lastName(),
                        "@" + request.username(),
                        request.email(),
                        "mar\u00e7o de 2026"
                )
        );
    }

    private String resolveEmail(String identifier) {
        return identifier.contains("@") ? identifier : identifier + "@ecofood.local";
    }
}
