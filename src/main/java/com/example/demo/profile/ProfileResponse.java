package com.example.demo.profile;

public record ProfileResponse(
        String name,
        String username,
        String memberSince,
        EnvironmentalImpactResponse environmentalImpact
) {
}
