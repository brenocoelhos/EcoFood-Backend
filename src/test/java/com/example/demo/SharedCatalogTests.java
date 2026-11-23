package com.example.demo;

import com.example.demo.catalog.*;
import com.example.demo.catalog.CatalogDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SharedCatalogTests extends VerifiedIdentityTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired CatalogService service;
    @Autowired ObjectMapper json;
    @MockitoSpyBean CatalogRepository repo;
    @MockitoBean Clock clock;
    UUID a,b,ma,mb;
    final LocalDate today=LocalDate.of(2026,10,22);

    void time(String instant) {
        Clock fixed=Clock.fixed(Instant.parse(instant),ZoneOffset.UTC);
        when(clock.instant()).thenReturn(fixed.instant());
        doAnswer(i->fixed.withZone(i.getArgument(0))).when(clock).withZone(any());
    }
    @BeforeEach void setup() {
        time("2026-10-22T15:00:00Z");
        a=UUID.randomUUID();b=UUID.randomUUID();ma=UUID.randomUUID();mb=UUID.randomUUID();
        repo.createMarket(ma,a,market("Mercado A","11111111000111"));
        repo.createMarket(mb,b,market("Mercado B","22222222000122"));
    }
    @AfterEach void cleanup() {
        db.update("delete from ecofood.markets where id in (?,?)",ma,mb);
    }
    MarketInput market(String name,String doc) {
        return new MarketInput(name,"11999999999",doc,"01001000","SP","São Paulo","Centro","Rua","1","","Dono","Teste","dono","private@example.com","11999999999","");
    }
    ProductInput input(String name) {
        return new ProductInput(name,"secos-enlatados",null,today.plusDays(5),new BigDecimal("20.00"),new BigDecimal("10.00"),7);
    }
    UUID source(String name,boolean active,boolean expired,boolean image) throws Exception {
        UUID id=service.createProduct(b,input(name)).id();
        db.update("update ecofood.products set is_active=?,expiration_date=? where id=?",active,expired?today.minusDays(1):today.plusDays(3),id);
        if(image) repo.saveImage(id,"image/png",png(0));
        return id;
    }
    byte[] png(int color) throws Exception {
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,color);
        var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray();
    }
    String body(UUID source) throws Exception {
        return json.writeValueAsString(new FromCatalogInput(source,"Café Marca Azul 500 g","secos-enlatados",today,today.plusDays(10),new BigDecimal("9.00"),new BigDecimal("6.00"),3));
    }
    UUID copy(UUID source) throws Exception {
        var result=mvc.perform(post("/api/owner/products/from-catalog").with(jwt().jwt(j->j.subject(a.toString())))
            .contentType(MediaType.APPLICATION_JSON).content(body(source)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.stockQuantity").value(3))
            .andExpect(jsonPath("$.originalPrice").value(9.00)).andExpect(jsonPath("$.offerPrice").value(6.00))
            .andExpect(jsonPath("$.expirationDate").value(today.plusDays(10).toString()))
            .andExpect(jsonPath("$.name").value("Café Marca Azul 500 g")).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
    @Test void findsActiveInactiveExpiredAndAccentOrBrandWithoutPrivateFields() throws Exception {
        source("Café Marca Azul 500 g",true,false,true);
        source("CAFÉ Marca Azul 1 kg",false,false,true);
        source("Café Marca Azul 250 g",false,true,true);
        for(String q:List.of("cafe","CAFÉ","marca azul")) {
            mvc.perform(get("/api/owner/product-catalog").param("q",q).with(jwt().jwt(j->j.subject(a.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(3)))
                .andExpect(jsonPath("$[0]",aMapWithSize(5)))
                .andExpect(jsonPath("$[0].marketName").value("Mercado B"))
                .andExpect(jsonPath("$[0].imageUri",org.hamcrest.Matchers.startsWith("/api/owner/product-catalog/")))
                .andExpect(jsonPath("$[0].offerPrice").doesNotExist()).andExpect(jsonPath("$[0].stockQuantity").doesNotExist());
        }
    }
    @Test void rankingAndLimitsAreStableAndWildcardsAreLiteral() throws Exception {
        source("Café",true,false,false);source("Café Azul",true,false,false);source("Bom Café",true,false,false);
        var req=get("/api/owner/product-catalog").param("q","cafe").param("limit","2").with(jwt().jwt(j->j.subject(a.toString())));
        String first=mvc.perform(req).andExpect(jsonPath("$",hasSize(2))).andExpect(jsonPath("$[0].name").value("Café"))
            .andExpect(jsonPath("$[1].name").value("Café Azul")).andReturn().getResponse().getContentAsString();
        assertEquals(first,mvc.perform(req).andReturn().getResponse().getContentAsString());
        for(String q:List.of("c","  ")) mvc.perform(get("/api/owner/product-catalog").param("q",q).with(jwt().jwt(j->j.subject(a.toString())))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/owner/product-catalog").param("q","ca").param("limit","9").with(jwt().jwt(j->j.subject(a.toString())))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/owner/product-catalog").param("q","%_").with(jwt().jwt(j->j.subject(a.toString())))).andExpect(jsonPath("$",hasSize(0)));
    }
    @Test void visitorAndCustomerCannotSuggestCopyOrReadCatalogImageEvenWithSpoofedMetadata() throws Exception {
        UUID id=source("Café",false,true,true);
        var customer=jwt().jwt(j->j.subject(UUID.randomUUID().toString()).claim("user_metadata",Map.of("accountType","marketOwner")));
        for(String path:List.of("/api/owner/product-catalog?q=cafe","/api/owner/product-catalog/"+id+"/image")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(customer)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/owner/products/from-catalog").contentType(MediaType.APPLICATION_JSON).content(body(id))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/owner/products/from-catalog").with(customer).contentType(MediaType.APPLICATION_JSON).content(body(id))).andExpect(status().isForbidden());
        mvc.perform(get("/api/products/"+id+"/image").with(jwt().jwt(j->j.subject(a.toString())))).andExpect(status().isNotFound());
        mvc.perform(get("/api/owner/product-catalog/"+id+"/image").with(jwt().jwt(j->j.subject(a.toString()))))
            .andExpect(status().isOk()).andExpect(content().bytes(png(0))).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(delete("/api/owner/products/"+id).with(jwt().jwt(j->j.subject(a.toString())))).andExpect(status().isNotFound());
    }
    @Test void copiesActiveAndExpiredSourcesWithoutTransferringOwnershipOrBatchData() throws Exception {
        for(boolean expired:new boolean[]{false,true}) {
            UUID original=source("Café Marca Azul 500 g",!expired,expired,true);
            UUID clone=copy(original);assertNotEquals(original,clone);
            assertEquals(ma,db.queryForObject("select market_id from ecofood.products where id=?",UUID.class,clone));
            assertEquals(mb,db.queryForObject("select market_id from ecofood.products where id=?",UUID.class,original));
            assertEquals(7,repo.product(original).orElseThrow().product().stockQuantity());
            assertEquals("/api/products/"+clone+"/image",repo.product(clone).orElseThrow().product().imageUri());
            assertArrayEquals(png(0),repo.image(clone).orElseThrow().content());
        }
    }
    @Test void copiedPhotoSurvivesSourceReplacementDeactivationDeletionAndAcceptsNewUpload() throws Exception {
        UUID original=source("Café",true,false,true),clone=copy(original);
        repo.saveImage(original,"image/png",png(0xffffff));service.active(b,original,false);
        assertArrayEquals(png(0),repo.image(clone).orElseThrow().content());
        service.delete(b,original);
        assertArrayEquals(png(0),repo.image(clone).orElseThrow().content());
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT,"/api/owner/products/"+clone+"/image")
            .file(new MockMultipartFile("file","new.png","image/png",png(0xff6600))).with(jwt().jwt(j->j.subject(a.toString()))))
            .andExpect(status().isOk());
        assertArrayEquals(png(0xff6600),repo.image(clone).orElseThrow().content());
    }
    @Test void missingSourceIs404NoPhotoSourceIsValidAndInvalidBatchIsRejected() throws Exception {
        mvc.perform(post("/api/owner/products/from-catalog").with(jwt().jwt(j->j.subject(a.toString())))
            .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID()))).andExpect(status().isNotFound());
        assertEquals(0,repo.ownerProducts(ma,100,0).size());
        UUID id=source("Café",true,false,false),clone=copy(id);
        assertNull(repo.product(clone).orElseThrow().product().imageUri());
        mvc.perform(post("/api/owner/products/from-catalog").with(jwt().jwt(j->j.subject(a.toString())))
            .contentType(MediaType.APPLICATION_JSON).content(body(id).replace(today.plusDays(10).toString(),today.minusDays(1).toString())))
            .andExpect(status().isBadRequest());
    }
    @Test void copyFailureRollsBackInsertedProductAndPhoto() throws Exception {
        UUID original=source("Café",true,false,true);
        doAnswer(invocation->{invocation.callRealMethod();throw new org.springframework.dao.DataIntegrityViolationException("simulated storage failure");})
            .when(repo).saveImage(any(UUID.class),anyString(),any(byte[].class));
        mvc.perform(post("/api/owner/products/from-catalog").with(jwt().jwt(j->j.subject(a.toString())))
            .contentType(MediaType.APPLICATION_JSON).content(body(original))).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message",containsString("Nenhuma oferta foi criada")));
        assertEquals(0,repo.ownerProducts(ma,100,0).size());
        assertEquals(1,db.queryForObject("select count(*) from ecofood.product_images i join ecofood.products p on p.id=i.product_id where p.market_id in (?,?)",Integer.class,ma,mb));
    }
    @Test void rateLimitIsPersistedPerOwnerAndResetsAfterMinute() throws Exception {
        db.update("update ecofood.markets set catalog_window=?,catalog_requests=119 where id=?",java.sql.Timestamp.from(clock.instant()),ma);
        mvc.perform(get("/api/owner/product-catalog?q=cafe").with(jwt().jwt(j->j.subject(a.toString())))).andExpect(status().isOk());
        mvc.perform(get("/api/owner/product-catalog?q=cafe").with(jwt().jwt(j->j.subject(a.toString())))).andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/owner/product-catalog?q=cafe").with(jwt().jwt(j->j.subject(b.toString())))).andExpect(status().isOk());
        time("2026-10-22T15:01:01Z");
        mvc.perform(get("/api/owner/product-catalog?q=cafe").with(jwt().jwt(j->j.subject(a.toString())))).andExpect(status().isOk());
    }
    @Test void brasiliaMidnightExpirationIsIdempotentAndPreservesEveryOtherColumnAndPhoto() throws Exception {
        UUID id=source("Café Marca Azul 500 g",true,false,true);
        db.update("update ecofood.products set expiration_date=? where id=?",today,id);
        Map<String,Object> before=new HashMap<>(db.queryForMap("select * from ecofood.products where id=?",id));before.remove("is_active");
        time("2026-10-23T02:59:59Z");assertEquals(0,service.expireProducts());assertTrue(repo.product(id).orElseThrow().product().isActive());
        time("2026-10-23T03:00:00Z");
        mvc.perform(get("/api/products/"+id)).andExpect(status().isNotFound()); // public filtering works even before job
        assertEquals(1,service.expireProducts());assertEquals(0,service.expireProducts());
        Map<String,Object> after=new HashMap<>(db.queryForMap("select * from ecofood.products where id=?",id));after.remove("is_active");assertEquals(before,after);
        assertArrayEquals(png(0),repo.image(id).orElseThrow().content());
        mvc.perform(get("/api/products/"+id+"/image").with(jwt().jwt(j->j.subject(b.toString())))).andExpect(content().bytes(png(0)));
        mvc.perform(get("/api/owner/products").with(jwt().jwt(j->j.subject(b.toString())))).andExpect(jsonPath("$[0].isActive").value(false)).andExpect(jsonPath("$[0].imageUri").isString());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.active(b,id,true));
        service.updateProduct(b,id,input("Novo lote"));assertFalse(repo.product(id).orElseThrow().product().isActive());
        service.active(b,id,true);assertTrue(repo.product(id).orElseThrow().product().isActive());
        service.active(b,id,false);assertArrayEquals(png(0),repo.image(id).orElseThrow().content());
    }
    @Test void editingExpiredProductBeforeSweepStillRequiresExplicitReactivation() throws Exception {
        UUID id=source("Café",true,true,true);
        service.updateProduct(b,id,input("Café"));assertFalse(repo.product(id).orElseThrow().product().isActive());
        assertArrayEquals(png(0),repo.image(id).orElseThrow().content());
    }
    @Test void startupAndPeriodicJobInvokeIdempotentExpiration() throws Exception {
        source("Café",true,true,true);
        var job=new ProductExpirationJob(service);job.onStartup();job.expire();
        assertFalse(repo.ownerProducts(mb,8,0).get(0).product().isActive());
        assertNotNull(ProductExpirationJob.class.getMethod("onStartup").getAnnotation(org.springframework.context.event.EventListener.class));
        var schedule=ProductExpirationJob.class.getMethod("expire").getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertEquals("America/Sao_Paulo",schedule.zone());
    }
}
