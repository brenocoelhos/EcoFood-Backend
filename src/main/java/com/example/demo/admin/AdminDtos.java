package com.example.demo.admin;
import java.time.*;
import java.util.*;
import jakarta.validation.constraints.*;

public final class AdminDtos {
    private AdminDtos() {}
    public record Page<T>(List<T> items,long total,int page,int size) {}
    public record Overview(long markets,long activeProducts,long users) {}
    public record Market(UUID id,String name,UUID ownerId,String ownerUsername,String city,String state,String registrationStatus,long products,Instant createdAt) {}
    public record Product(UUID id,String name,UUID marketId,String marketName,LocalDate expirationDate,boolean isActive,int stockQuantity,String imageUri,Instant createdAt) {}
    public record User(UUID id,String username,String role,boolean suspended,Instant createdAt) {}
    public record ProductStatus(@NotNull Boolean isActive,@NotBlank @Size(max=500) String reason) {}
    public record Suspension(@NotNull Boolean suspended,@NotBlank @Size(max=500) String reason) {}
    public record Audit(UUID id,UUID actorId,UUID targetId,String action,Instant occurredAt,String reason,String previousValue,String newValue) {}
    public record Totals(long offersCreated,long productsSaved,long productsDeactivated,long marketsCreated) {}
    public record Series(LocalDate date,Totals totals) {}
    public record Report(LocalDate from,LocalDate to,String timezone,Instant activityTrackingSince,Totals totals,Page<Series> series) {}
}
