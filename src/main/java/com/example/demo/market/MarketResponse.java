package com.example.demo.market;

public record MarketResponse(
        String id,
        String name,
        String distance,
        String type,
        String displayDistance,
        int availableItems,
        String availableItemsLabel,
        String maxDiscount
) {
}
