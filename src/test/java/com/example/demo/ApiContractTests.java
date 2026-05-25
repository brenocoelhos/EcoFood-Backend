package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiContractTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginAcceptsFrontendEmailField() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "breno@email.com",
                                  "password": "12345678",
                                  "rememberLogin": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("dev-token"))
                .andExpect(jsonPath("$.user.email").value("breno@email.com"));
    }

    @Test
    void registerRequiresAcceptedTerms() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Breno",
                                  "lastName": "Coelho",
                                  "username": "brenocoelhos",
                                  "email": "breno@email.com",
                                  "phone": "(12) 99999-0000",
                                  "password": "12345678",
                                  "acceptedTerms": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("acceptedTerms"));
    }

    @Test
    void marketsExposeLabelsUsedByFrontendCards() throws Exception {
        mockMvc.perform(get("/api/markets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Ger\u00f4nimo"))
                .andExpect(jsonPath("$[0].displayDistance").value("800m \u00b7 mercado"))
                .andExpect(jsonPath("$[0].availableItemsLabel").value("12 itens"))
                .andExpect(jsonPath("$[0].maxDiscount").value("at\u00e9 60%"));
    }

    @Test
    void productsExposeBadgesAndFormattedPricesUsedByFrontendCards() throws Exception {
        mockMvc.perform(get("/api/markets/geronimo/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].expiresIn").value("Hoje"))
                .andExpect(jsonPath("$[0].urgent").value(true))
                .andExpect(jsonPath("$[0].discount").value("-30%"))
                .andExpect(jsonPath("$[0].oldPriceFormatted").value("R$ 13,80"))
                .andExpect(jsonPath("$[0].priceFormatted").value("R$ 11,90"));
    }
}
