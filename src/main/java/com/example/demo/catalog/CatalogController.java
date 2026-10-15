package com.example.demo.catalog;

import static com.example.demo.catalog.CatalogDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Validated
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service) {this.service=service;}
    private UUID owner(Jwt jwt) {return UUID.fromString(jwt.getSubject());}
    @GetMapping("/categories") public List<Category> categories() {return service.categories();}
    @GetMapping("/markets") public List<PublicMarket> markets(@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit,@RequestParam(defaultValue="0") @Min(0) int offset) {return service.markets(limit,offset);}
    @GetMapping("/markets/{id}") public PublicMarket market(@PathVariable UUID id) {return service.market(id);}
    @GetMapping("/markets/{id}/image") public ResponseEntity<byte[]> marketImage(@PathVariable UUID id) {
        var image=service.marketImage(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
            .header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore()).body(image.content());
    }
    @PutMapping(value="/owner/market/image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public OwnerMarket uploadMarketImage(@AuthenticationPrincipal Jwt jwt,@RequestPart("file") MultipartFile file) {
        return service.marketImage(owner(jwt),file);
    }
    @GetMapping("/markets/{id}/products") public List<PublicProduct> products(@PathVariable UUID id,
        @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit,@RequestParam(defaultValue="0") @Min(0) int offset) {return service.products(id,null,null,limit,offset);}
    @GetMapping({"/products","/products/search"}) public List<PublicProduct> search(
        @RequestParam(defaultValue="") @Size(max=160) String q,@RequestParam(required=false) @Size(max=50) String category,
        @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit,@RequestParam(defaultValue="0") @Min(0) int offset) {return service.products(null,q,category,limit,offset);}
    @GetMapping("/products/{id}") public PublicProduct product(@PathVariable UUID id) {return service.product(id);}
    @GetMapping("/products/{id}/image") public ResponseEntity<byte[]> image(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) {
        var image=service.image(id,jwt==null?null:owner(jwt));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).header("X-Content-Type-Options","nosniff")
            .cacheControl(CacheControl.noStore()).body(image.content());
    }
    @GetMapping("/owner/market") public OwnerMarket ownMarket(@AuthenticationPrincipal Jwt jwt) {return service.ownerMarket(owner(jwt));}
    @PostMapping("/owner/market") @ResponseStatus(HttpStatus.CREATED)
    public OwnerMarket createMarket(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody MarketInput input) {return service.createMarket(owner(jwt),input);}
    @PutMapping("/owner/market") public OwnerMarket updateMarket(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody MarketInput input) {return service.updateMarket(owner(jwt),input);}
    @PutMapping("/owner/market/hours") public OwnerMarket hours(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody HoursInput input) {return service.hours(owner(jwt),input);}
    @GetMapping("/owner/products") public List<OwnerProduct> ownProducts(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit,@RequestParam(defaultValue="0") @Min(0) int offset) {return service.ownerProducts(owner(jwt),limit,offset);}
    @GetMapping("/owner/products/{id}") public OwnerProduct ownProduct(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {return service.ownerProduct(owner(jwt),id);}
    @PostMapping("/owner/products") @ResponseStatus(HttpStatus.CREATED)
    public OwnerProduct createProduct(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ProductInput input) {return service.createProduct(owner(jwt),input);}
    @PutMapping("/owner/products/{id}") public OwnerProduct updateProduct(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody ProductInput input) {return service.updateProduct(owner(jwt),id,input);}
    @PatchMapping("/owner/products/{id}/status") public OwnerProduct status(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody ProductStatus input) {return service.active(owner(jwt),id,input.isActive());}
    @DeleteMapping("/owner/products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {service.delete(owner(jwt),id);}
    @PutMapping(value="/owner/products/{id}/image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public OwnerProduct upload(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestPart("file") MultipartFile file) {return service.image(owner(jwt),id,file);}
}
