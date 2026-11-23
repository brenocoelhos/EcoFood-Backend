package com.example.demo.admin;
import static com.example.demo.admin.AdminDtos.*;
import java.util.UUID;
import java.time.LocalDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@Validated
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) {this.service=service;}
    @GetMapping("/overview") public Overview overview() {return service.overview();}
    @GetMapping("/markets") public Page<Market> markets(@RequestParam(defaultValue="") @Size(max=160) String q,
        @RequestParam(required=false) @Pattern(regexp="[A-Z]{2}") String state,@RequestParam(required=false) String status,
        @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {return service.markets(q,state,status,page,size);}
    @GetMapping("/markets/{id}") public Market market(@PathVariable UUID id) {return service.market(id);}
    @GetMapping("/products") public Page<Product> products(@RequestParam(defaultValue="") @Size(max=160) String q,
        @RequestParam(required=false) UUID marketId,@RequestParam(required=false) String status,
        @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {return service.products(q,marketId,status,page,size);}
    @GetMapping("/products/{id}") public Product product(@PathVariable UUID id) {return service.product(id);}
    @GetMapping("/products/{id}/image") public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        var image=service.image(id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
            .header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore()).body(image.content());
    }
    @PatchMapping("/products/{id}/status") public Product moderate(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody ProductStatus input) {return service.moderate(UUID.fromString(jwt.getSubject()),id,input);}
    @GetMapping("/users") public Page<User> users(@RequestParam(defaultValue="") @Size(max=160) String q,
        @RequestParam(required=false) String role,@RequestParam(required=false) Boolean suspended,
        @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {return service.users(q,role,suspended,page,size);}
    @GetMapping("/users/{id}") public User user(@PathVariable UUID id) {return service.user(id);}
    @PatchMapping("/users/{id}/suspension") public User suspension(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Suspension input) {return service.suspension(UUID.fromString(jwt.getSubject()),id,input);}
    @GetMapping("/audit") public Page<Audit> audit(@RequestParam(required=false) UUID actorId,@RequestParam(required=false) UUID targetId,
        @RequestParam(required=false) @Size(max=50) String action,@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {return service.audit(actorId,targetId,action,page,size);}
    @GetMapping("/reports") public Report report(@RequestParam LocalDate from,@RequestParam LocalDate to,
        @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="31") @Min(1) @Max(100) int size) {return service.report(from,to,page,size);}
}
