package com.example.demo.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String name,
        @NotBlank String lastName,
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\(\\d{2}\\) \\d{4,5}-\\d{4}") String phone,
        @NotBlank @Size(min = 8) String password,
        @AssertTrue(message = "must be accepted")
        boolean acceptedTerms
) {
}
