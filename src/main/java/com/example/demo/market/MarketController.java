package com.example.demo.market;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/markets")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping
    public List<MarketResponse> listMarkets() {
        return marketService.listMarkets();
    }

    @GetMapping("/{marketId}")
    public MarketResponse getMarket(@PathVariable String marketId) {
        return marketService.getMarket(marketId);
    }

    @GetMapping("/{marketId}/products")
    public List<ProductResponse> listProducts(@PathVariable String marketId) {
        return marketService.listProducts(marketId);
    }
}
