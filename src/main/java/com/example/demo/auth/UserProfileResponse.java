package com.example.demo.auth;

public record UserProfileResponse(
        String name,
        String username,
        String email,
        String memberSince
) {
}
