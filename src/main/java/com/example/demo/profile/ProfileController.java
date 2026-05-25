package com.example.demo.profile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @GetMapping
    public ProfileResponse getProfile() {
        return new ProfileResponse(
                "Breno Coelho",
                "@brenocoelhos",
                "membro desde mar\u00e7o de 2026",
                new EnvironmentalImpactResponse(103, "Itens", "Desperd\u00edcio evitado de:")
        );
    }
}
