package com.example.demo;

import com.example.demo.admin.*;
import com.example.demo.catalog.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="ADMIN_EMAILS=  Admin@Example.Invalid  , SECOND@example.invalid ")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityTests {
    static final UUID ADMIN=UUID.fromString("00000000-0000-4000-8000-000000000011");
    static final UUID CUSTOMER=UUID.fromString("00000000-0000-4000-8000-000000000012");
    static final UUID OWNER=UUID.fromString("00000000-0000-4000-8000-000000000013");
    static final HttpServer provider;
    static final ECKey key;
    static final AtomicInteger calls=new AtomicInteger();
    static final AtomicInteger providerStatus=new AtomicInteger(200);
    static final AtomicBoolean confirmed=new AtomicBoolean(true),mismatch=new AtomicBoolean(false),emailChanged=new AtomicBoolean(false);
    static {
        try {
            key=new ECKeyGenerator(Curve.P_256).keyID("admin-test-key").generate();
            provider=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            provider.createContext("/jwks",exchange->{
                byte[] bytes=new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
                try(var out=exchange.getResponseBody()){out.write(bytes);}
            });
            provider.createContext("/auth/v1/user",exchange->{
                calls.incrementAndGet();
                String id;
                try {id=SignedJWT.parse(exchange.getRequestHeaders().getFirst("Authorization").substring(7)).getJWTClaimsSet().getSubject();}
                catch(Exception e){id=CUSTOMER.toString();}
                String email=id.equals(ADMIN.toString()) && !emailChanged.get()?"Admin@Example.Invalid":"user@example.invalid";
                if(mismatch.get()) id=CUSTOMER.toString();
                String body="{\"id\":\""+id+"\",\"email\":\""+email+"\",\"email_confirmed_at\":"+(confirmed.get()?"\"2026-01-01T00:00:00Z\"":"null")+",\"user_metadata\":{\"role\":\"admin\"}}";
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(providerStatus.get(),bytes.length);
                try(var out=exchange.getResponseBody()){out.write(bytes);}
            });provider.start();
        } catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    static String url() {return "http://127.0.0.1:"+provider.getAddress().getPort();}
    @DynamicPropertySource static void props(DynamicPropertyRegistry p) {
        p.add("supabase.url",AdminSecurityTests::url);p.add("supabase.publishable-key",()->"public-test-key");
        p.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",()->url()+"/auth/v1");
        p.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",()->url()+"/jwks");
    }
    @AfterAll static void stop() {provider.stop(0);}
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired CatalogRepository repo;
    @Autowired AdminAccessService access;
    @MockitoSpyBean AdminAllowlist allowlist;
    UUID market,product;
    @BeforeEach void seed() {
        calls.set(0);providerStatus.set(200);confirmed.set(true);mismatch.set(false);emailChanged.set(false);
        for(UUID id:List.of(ADMIN,CUSTOMER,OWNER)) db.update("INSERT INTO ecofood.admin_users(id) VALUES (?)",id);
        market=UUID.randomUUID();product=UUID.randomUUID();
        repo.createMarket(market,OWNER,new CatalogDtos.MarketInput("Admin Test Market","11999999999","33222222000155","01001000","SP","São Paulo","Centro","Rua","1","","Owner","Test","owner","hidden@example.invalid","11999999999",""));
        var p=new CatalogDtos.ProductInput("Café Teste","secos-enlatados",null,LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1),new BigDecimal("10.00"),new BigDecimal("8.00"),4);
        repo.createProduct(product,market,p,"cafe teste");repo.saveImage(product,"image/png",new byte[]{1,2,3});
    }
    @AfterEach void cleanup() {
        db.update("DELETE FROM ecofood.markets WHERE id=?",market);
        for(UUID id:List.of(ADMIN,CUSTOMER,OWNER)) {
            db.update("DELETE FROM ecofood.admin_audit WHERE actor_id=? OR target_id=?",id,id);
            db.update("DELETE FROM ecofood.user_access WHERE user_id=?",id);db.update("DELETE FROM ecofood.admin_users WHERE id=?",id);
        }
        db.update("DELETE FROM ecofood.catalog_events WHERE target_id=?",product);
    }
    String token(UUID subject) throws Exception {return token(subject,url()+"/auth/v1",key);}
    String token(UUID subject,String issuer,ECKey signingKey) throws Exception {
        var claims=new JWTClaimsSet.Builder().subject(subject.toString()).issuer(issuer).audience("authenticated")
            .expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("role","authenticated")
            .claim("email","admin@example.invalid").claim("email_verified",true)
            .claim("user_metadata",Map.of("role","admin","accountType","admin")).build();
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(),claims);
        jwt.sign(new ECDSASigner(signingKey));return jwt.serialize();
    }
    @Test void allAdminSectionsRejectMissingCustomerAndOwnerAndAcceptVerifiedAdmin() throws Exception {
        for(String path:List.of("/overview","/markets","/markets/"+market,"/products","/products/"+product,"/products/"+product+"/image","/users","/users/"+CUSTOMER,"/audit","/reports?from=2026-11-01&to=2026-11-05")) {
            mvc.perform(get("/api/admin"+path).secure(true)).andExpect(status().isUnauthorized());
            for(UUID id:List.of(CUSTOMER,OWNER)) mvc.perform(get("/api/admin"+path).secure(true).header("Authorization","Bearer "+token(id))).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin"+path).secure(true).header("Authorization","Bearer "+token(ADMIN))).andExpect(status().isOk());
        }
    }
    @Test void profilePromotesOnlyVerifiedConfirmedEmailAndReturnsRealCounts() throws Exception {
        String jwt=token(ADMIN);
        mvc.perform(get("/api/profile").secure(true).header("Authorization","Bearer "+jwt)).andExpect(status().isOk()).andExpect(jsonPath("$.accountType").value("admin"));
        mvc.perform(get("/api/admin/overview").secure(true).header("Authorization","Bearer "+jwt))
            .andExpect(status().isOk()).andExpect(jsonPath("$.markets").value(1)).andExpect(jsonPath("$.activeProducts").value(1)).andExpect(jsonPath("$.users").value(3));
        assertEquals(2,calls.get());assertTrue(db.queryForObject("SELECT is_admin FROM ecofood.user_access WHERE user_id=?",Boolean.class,ADMIN));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM ecofood.admin_audit WHERE actor_id=? AND action='ADMIN_GRANTED'",Integer.class,ADMIN));
    }
    @Test void unconfirmedEmailNeverPromotesDespiteJwtClaims() throws Exception {
        confirmed.set(false);
        mvc.perform(get("/api/admin/overview").secure(true).header("Authorization","Bearer "+token(ADMIN))).andExpect(status().isForbidden());
        assertFalse(db.queryForObject("SELECT is_admin FROM ecofood.user_access WHERE user_id=?",Boolean.class,ADMIN));
    }
    @Test void badSignatureIssuerAndSupabaseSubjectMismatchAre401() throws Exception {
        for(String jwt:List.of(token(ADMIN,"https://wrong.invalid",key),token(ADMIN,url()+"/auth/v1",new ECKeyGenerator(Curve.P_256).generate()),"invalid"))
            mvc.perform(get("/api/admin/overview").secure(true).header("Authorization","Bearer "+jwt)).andExpect(status().isUnauthorized());
        assertEquals(0,calls.get());mismatch.set(true);
        mvc.perform(get("/api/admin/overview").secure(true).header("Authorization","Bearer "+token(ADMIN))).andExpect(status().isUnauthorized());
    }
    @Test void removedEmailOrEmailChangeRevokesExistingRoleWithSameToken() throws Exception {
        String jwt=token(ADMIN);
        mvc.perform(get("/api/admin/overview").secure(true).header("Authorization","Bearer "+jwt)).andExpect(status().isOk());
        doReturn(false).when(allowlist).contains(anyString());doReturn(Set.of()).when(allowlist).hashes();
        mvc.perform(get("/api/admin/overview").secure(true).header("Authorization","Bearer "+jwt)).andExpect(status().isForbidden());
        assertFalse(db.queryForObject("SELECT is_admin FROM ecofood.user_access WHERE user_id=?",Boolean.class,ADMIN));
        reset(allowlist);emailChanged.set(true);
        mvc.perform(get("/api/profile").secure(true).header("Authorization","Bearer "+jwt)).andExpect(status().isOk()).andExpect(jsonPath("$.accountType").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void reconciliationRevokesOfflineAdminsAndEmptyListGrantsNobody() throws Exception {
        mvc.perform(get("/api/admin/overview").secure(true).header("Authorization","Bearer "+token(ADMIN))).andExpect(status().isOk());
        doReturn(Set.of()).when(allowlist).hashes();access.revokeRemoved();
        assertFalse(db.queryForObject("SELECT is_admin FROM ecofood.user_access WHERE user_id=?",Boolean.class,ADMIN));
        assertFalse(new AdminAllowlist(" , ").contains("admin@example.invalid"));
        assertTrue(new AdminAllowlist(" ADMIN@EXAMPLE.INVALID,second@example.invalid ").contains(" admin@example.invalid "));
    }
    @Test void providerFailuresFailClosedAndNeverExposeDetails() throws Exception {
        providerStatus.set(503);
        mvc.perform(get("/api/admin/overview").secure(true).header("Authorization","Bearer "+token(ADMIN)))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Não foi possível verificar a sessão. Tente novamente."));
        providerStatus.set(401);
        mvc.perform(get("/api/profile").secure(true).header("Authorization","Bearer "+token(ADMIN))).andExpect(status().isUnauthorized());
    }
    @Test void cleartextOrSpoofedProxyHeaderCannotPromoteAdmin() throws Exception {
        mvc.perform(get("/api/admin/overview").header("Authorization","Bearer "+token(ADMIN)).header("X-Forwarded-Proto","https"))
            .andExpect(status().isUpgradeRequired());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM ecofood.user_access WHERE user_id=?",Integer.class,ADMIN));
    }
    @Test void mutationsRequireRoleReasonAndAuditAtomically() throws Exception {
        for(UUID id:List.of(CUSTOMER,OWNER)) mvc.perform(patch("/api/admin/products/"+product+"/status").secure(true).header("Authorization","Bearer "+token(id))
            .contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false,\"reason\":\"Teste\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/products/"+product+"/status").secure(true).contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false,\"reason\":\"Teste\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/products/"+product+"/status").secure(true).header("Authorization","Bearer "+token(ADMIN))
            .contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false,\"reason\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/products/"+product+"/status").secure(true).header("Authorization","Bearer "+token(ADMIN))
            .contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false,\"reason\":\"Revisão do anúncio\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false));
        assertArrayEquals(new byte[]{1,2,3},repo.image(product).orElseThrow().content());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM ecofood.admin_audit WHERE actor_id=? AND target_id=? AND action='PRODUCT_STATUS'",Integer.class,ADMIN,product));
        db.update("UPDATE ecofood.products SET expiration_date=? WHERE id=?",LocalDate.now().minusDays(1),product);
        mvc.perform(patch("/api/admin/products/"+product+"/status").secure(true).header("Authorization","Bearer "+token(ADMIN))
            .contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":true,\"reason\":\"Teste\"}")).andExpect(status().isBadRequest());
    }
    @Test void suspensionBlocksOldTokenEverywhereAndRestoreWorks() throws Exception {
        String customer=token(CUSTOMER);
        for(boolean suspended:new boolean[]{true,false}) {
            mvc.perform(patch("/api/admin/users/"+CUSTOMER+"/suspension").secure(true).header("Authorization","Bearer "+token(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"suspended\":"+suspended+",\"reason\":\"Teste de suspensão\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.suspended").value(suspended));
            mvc.perform(get("/api/profile").secure(true).header("Authorization","Bearer "+customer))
                .andExpect(status().is(suspended?403:200));
        }
        assertEquals(2,db.queryForObject("SELECT count(*) FROM ecofood.admin_audit WHERE target_id=? AND action IN ('USER_SUSPENDED','USER_RESTORED')",Integer.class,CUSTOMER));
    }
    @Test void listsArePagedFilteredAndContainNoEmailOrSecrets() throws Exception {
        mvc.perform(get("/api/admin/users?size=1&role=customer").secure(true).header("Authorization","Bearer "+token(ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items",hasSize(1)))
            .andExpect(jsonPath("$.items[0].email").doesNotExist()).andExpect(jsonPath("$.items[0].encrypted_password").doesNotExist());
        mvc.perform(get("/api/admin/markets?q=admin&state=SP&status=registered").secure(true).header("Authorization","Bearer "+token(ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].ownerId").value(OWNER.toString()));
        mvc.perform(get("/api/admin/products?q=cafe&size=1").secure(true).header("Authorization","Bearer "+token(ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/admin/users?size=101").secure(true).header("Authorization","Bearer "+token(ADMIN))).andExpect(status().isBadRequest());
    }
    @Test void rateLimitIsServerSideAndReportsUseBrasiliaWindow() throws Exception {
        String jwt=token(ADMIN);
        db.update("INSERT INTO ecofood.catalog_events(id,target_id,event_type,occurred_at) VALUES (?,?,?,?)",UUID.randomUUID(),product,"PRODUCT_DEACTIVATED",java.sql.Timestamp.from(Instant.parse("2026-11-05T02:59:59Z")));
        mvc.perform(get("/api/admin/reports?from=2026-11-04&to=2026-11-05&size=1").secure(true).header("Authorization","Bearer "+jwt))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totals.productsDeactivated").value(1))
            .andExpect(jsonPath("$.series.total").value(2)).andExpect(jsonPath("$.series.items[0].date").value("2026-11-04"))
            .andExpect(jsonPath("$.series.items[0].totals.productsDeactivated").value(1));
        db.update("UPDATE ecofood.user_access SET rate_window=CURRENT_TIMESTAMP,rate_count=120 WHERE user_id=?",ADMIN);
        mvc.perform(get("/api/admin/overview").secure(true).header("Authorization","Bearer "+jwt)).andExpect(status().isTooManyRequests());
    }
}
