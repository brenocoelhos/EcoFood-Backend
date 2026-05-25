package com.example.demo.market;

import java.math.BigDecimal;

public record ProductResponse(
        String id,
        String name,
        String expiresIn,
        boolean urgent,
        boolean fresh,
        int discountPercentage,
        String discount,
        BigDecimal oldPrice,
        String oldPriceFormatted,
        BigDecimal price,
        String priceFormatted
) {
}
