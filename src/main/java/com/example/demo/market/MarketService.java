package com.example.demo.market;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MarketService {

    private final List<MarketResponse> markets = List.of(
            market("geronimo", "Ger\u00f4nimo", "800m", "mercado", 12, "at\u00e9 60%"),
            market("hortifruti-centro", "Hortifruti Centro", "1,2km", "hortifruti", 8, "at\u00e9 45%"),
            market("padaria-boa-massa", "Padaria Boa Massa", "1,8km", "padaria", 5, "at\u00e9 35%")
    );

    private final Map<String, List<ProductResponse>> productsByMarket = Map.of(
            "geronimo", defaultProducts(),
            "hortifruti-centro", defaultProducts(),
            "padaria-boa-massa", defaultProducts()
    );

    public List<MarketResponse> listMarkets() {
        return markets;
    }

    public MarketResponse getMarket(String marketId) {
        return markets.stream()
                .filter(market -> market.id().equals(marketId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Market not found"));
    }

    public List<ProductResponse> listProducts(String marketId) {
        if (!productsByMarket.containsKey(marketId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Market not found");
        }

        return productsByMarket.get(marketId);
    }

    private static List<ProductResponse> defaultProducts() {
        return List.of(
                product("iogurte-natural-1", "Iogurte natural", "Hoje", true, false, 30, "13.80", "11.90"),
                product("iogurte-natural-2", "Iogurte natural", "2 dias", false, false, 30, "13.80", "11.90"),
                product("iogurte-natural-3", "Iogurte natural", "10 dias", false, true, 15, "13.80", "11.90"),
                product("iogurte-natural-4", "Iogurte natural", "2 dias", false, false, 30, "13.80", "11.90")
        );
    }

    private static MarketResponse market(String id, String name, String distance, String type, int availableItems, String maxDiscount) {
        return new MarketResponse(
                id,
                name,
                distance,
                type,
                distance + " \u00b7 " + type,
                availableItems,
                availableItems + " itens",
                maxDiscount
        );
    }

    private static ProductResponse product(
            String id,
            String name,
            String expiresIn,
            boolean urgent,
            boolean fresh,
            int discountPercentage,
            String oldPrice,
            String price
    ) {
        BigDecimal oldPriceValue = new BigDecimal(oldPrice);
        BigDecimal priceValue = new BigDecimal(price);

        return new ProductResponse(
                id,
                name,
                expiresIn,
                urgent,
                fresh,
                discountPercentage,
                "-" + discountPercentage + "%",
                oldPriceValue,
                formatCurrency(oldPriceValue),
                priceValue,
                formatCurrency(priceValue)
        );
    }

    private static String formatCurrency(BigDecimal value) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));
        return formatter.format(value).replace('\u00a0', ' ');
    }
}
