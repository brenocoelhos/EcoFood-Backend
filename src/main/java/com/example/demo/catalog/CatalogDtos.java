package com.example.demo.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {
    private CatalogDtos() {}
    public record Category(String id, String name) {}
    public record MarketInput(
        @NotBlank @Size(max=160) String marketName,
        @NotBlank @Size(max=25) String marketPhone,
        @NotBlank @Pattern(regexp="[0-9./-]{14,18}") String document,
        @NotBlank @Pattern(regexp="[0-9]{5}-?[0-9]{3}") String postalCode,
        @NotBlank @Pattern(regexp="[A-Z]{2}") String state,
        @NotBlank @Size(max=100) String city,
        @NotBlank @Size(max=100) String neighborhood,
        @NotBlank @Size(max=200) String address,
        @NotBlank @Size(max=20) String addressNumber,
        @Size(max=100) String complement,
        @NotBlank @Size(max=100) String name,
        @NotBlank @Size(max=100) String lastName,
        @NotBlank @Size(max=80) String username,
        @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(max=25) String phone,
        @Size(max=200) String referralSource) {}
    public record Period(@NotBlank @Pattern(regexp="([01][0-9]|2[0-3]):[0-5][0-9]") String opensAt,
                         @NotBlank @Pattern(regexp="([01][0-9]|2[0-3]):[0-5][0-9]") String closesAt) {}
    public record DayHours(@Min(0) @Max(6) int day, boolean enabled,
                           @NotNull @Size(max=12) List<@NotNull @Valid Period> periods) {}
    public record HoursInput(@NotNull @Size(min=7,max=7) List<@NotNull @Valid DayHours> days) {}
    public record OwnerMarket(UUID id, MarketInput profile, List<DayHours> businessHours, String imageUri) {}
    public record PublicMarket(UUID id, String name, String distance, String type,
        String displayDistance, int availableItems, String availableItemsLabel, String maxDiscount,
        String address, List<DayHours> businessHours, String imageUri) {}
    public record ProductInput(
        @NotBlank @Size(max=160) String name,
        @NotBlank @Size(max=50) String categoryId,
        LocalDate manufactureDate,
        @NotNull LocalDate expirationDate,
        @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal originalPrice,
        @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal offerPrice,
        @NotNull @Min(0) @Max(1000000) Integer stockQuantity) {}
    public record ProductStatus(@NotNull Boolean isActive) {}
    public record OwnerProduct(UUID id, boolean isActive, String name, String categoryId,
        LocalDate manufactureDate, LocalDate expirationDate, BigDecimal originalPrice,
        BigDecimal offerPrice, int stockQuantity, String imageUri) {}
    public record PublicProduct(UUID id, String name, String categoryId, LocalDate expirationDate,
        String expiresIn, boolean urgent, boolean fresh, int stockQuantity,
        int discountPercentage, String discount, BigDecimal oldPrice, String oldPriceFormatted,
        BigDecimal price, String priceFormatted, String imageUri,
        UUID marketId, String marketName, String marketDistance) {}
}
