package com.example.demo.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @JsonAlias({"email", "username"})
        @NotBlank String identifier,
        @NotBlank String password,
        boolean rememberLogin
) {
}
