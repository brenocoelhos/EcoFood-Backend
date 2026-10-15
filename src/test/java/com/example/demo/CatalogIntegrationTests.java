package com.example.demo;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    private final String owner=UUID.randomUUID().toString();
    private RequestPostProcessor user() {return jwt().jwt(j->j.subject(owner));}
    private String marketBody(String document) {
        return """
            {"marketName":"Mercado Teste","marketPhone":"11999999999","document":"%s",
             "postalCode":"01001000","state":"SP","city":"São Paulo","neighborhood":"Centro",
             "address":"Rua Teste","addressNumber":"12","complement":"","name":"Ana","lastName":"Silva",
             "username":"ana","email":"ana@example.com","phone":"11999999999","referralSource":""}
            """.formatted(document);
    }
    private UUID createMarket() throws Exception {
        mvc.perform(post("/api/owner/market").with(user()).contentType(MediaType.APPLICATION_JSON).content(marketBody("12345678000190")))
            .andExpect(status().isCreated());
        return db.queryForObject("select id from ecofood.markets where owner_id=?",UUID.class,UUID.fromString(owner));
    }
    private String productBody() {
        return """
            {"name":"Maçã fresca","categoryId":"frutas-legumes","manufactureDate":null,"expirationDate":"%s",
             "originalPrice":10.00,"offerPrice":7.50,"stockQuantity":8}
            """.formatted(LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(2));
    }
    private UUID createProduct() throws Exception {
        mvc.perform(post("/api/owner/products").with(user()).contentType(MediaType.APPLICATION_JSON).content(productBody()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.stockQuantity").value(8));
        return db.queryForObject("select p.id from ecofood.products p join ecofood.markets m on m.id=p.market_id where m.owner_id=?",UUID.class,UUID.fromString(owner));
    }
    @Test void crudPersistsAndPublicCatalogUsesActualPrices() throws Exception {
        UUID market=createMarket(), product=createProduct();
        mvc.perform(get("/api/markets/"+market+"/products")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].discountPercentage").value(25)).andExpect(jsonPath("$[0].priceFormatted").value("R$ 7,50"));
        mvc.perform(get("/api/products/search").param("q","maca")).andExpect(jsonPath("$",hasSize(1)));
        mvc.perform(get("/api/products").param("category","carnes-aves")).andExpect(jsonPath("$",hasSize(0)));
        mvc.perform(get("/api/markets/"+market)).andExpect(jsonPath("$.availableItems").value(1))
            .andExpect(jsonPath("$.maxDiscount").value("até 25%"));
        mvc.perform(put("/api/owner/products/"+product).with(user()).contentType(MediaType.APPLICATION_JSON)
            .content(productBody().replace("7.50","6.00"))).andExpect(status().isOk());
        assertEquals("6.00",db.queryForObject("select offer_price from ecofood.products where id=?",java.math.BigDecimal.class,product).toString());
        mvc.perform(patch("/api/owner/products/"+product+"/status").with(user()).contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/products/"+product)).andExpect(status().isNotFound());
        mvc.perform(get("/api/owner/products").with(user())).andExpect(jsonPath("$[0].isActive").value(false));
        mvc.perform(delete("/api/owner/products/"+product).with(user())).andExpect(status().isNoContent());
        assertEquals(0,db.queryForObject("select count(*) from ecofood.products where id=?",Integer.class,product));
    }
    @Test void anotherOwnerCannotReadModifyDeleteOrUpload() throws Exception {
        createMarket(); UUID product=createProduct();
        var stranger=jwt().jwt(j->j.subject(UUID.randomUUID().toString()));
        mvc.perform(post("/api/owner/market").with(stranger).contentType(MediaType.APPLICATION_JSON).content(marketBody("98765432000190"))).andExpect(status().isCreated());
        mvc.perform(get("/api/owner/products/"+product).with(stranger)).andExpect(status().isNotFound());
        mvc.perform(put("/api/owner/products/"+product).with(stranger).contentType(MediaType.APPLICATION_JSON).content(productBody())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/owner/products/"+product).with(stranger)).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/owner/products/"+product+"/image").file(new MockMultipartFile("file","x.png","image/png",new byte[]{1})).with(stranger)
            .with(r->{r.setMethod("PUT");return r;})).andExpect(status().isNotFound());
        mvc.perform(post("/api/owner/products").contentType(MediaType.APPLICATION_JSON).content(productBody())).andExpect(status().isUnauthorized());
    }
    @Test void badInputIsRejectedAndNoRowsAreCreated() throws Exception {
        createMarket();
        String past=LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(1).toString();
        String future=LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(2).toString();
        for(String body:new String[]{productBody().replace("7.50","11.00"),productBody().replace("7.50","-1"),
            productBody().replace("\"stockQuantity\":8","\"stockQuantity\":-1"),productBody().replace("\"stockQuantity\":8","\"stockQuantity\":1.5"),
            productBody().replace(future,past),productBody().replace("frutas-legumes","missing"),productBody().replace("7.50","7.555")}) {
            mvc.perform(post("/api/owner/products").with(user()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/products").param("limit","1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/search").param("q","%_'")).andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(0)));
        assertEquals(0,db.queryForObject("select count(*) from ecofood.products",Integer.class));
    }
    @Test void expiredAndOutOfStockProductsAreNotPublic() throws Exception {
        createMarket();UUID id=createProduct();
        db.update("update ecofood.products set expiration_date=? where id=?",LocalDate.now().minusDays(2),id);
        mvc.perform(get("/api/products")).andExpect(jsonPath("$",hasSize(0)));
        db.update("update ecofood.products set expiration_date=?,stock_quantity=0 where id=?",LocalDate.now().plusDays(5),id);
        mvc.perform(get("/api/products")).andExpect(jsonPath("$",hasSize(0)));
        mvc.perform(get("/api/owner/products").with(user())).andExpect(jsonPath("$",hasSize(1)));
    }
    @Test void duplicateMarketIsConflict() throws Exception {
        createMarket();
        mvc.perform(post("/api/owner/market").with(user()).contentType(MediaType.APPLICATION_JSON).content(marketBody("12345678000190"))).andExpect(status().isConflict());
    }
    @Test void hoursSupportOvernightAndRejectOverlapWithoutLosingSavedSchedule() throws Exception {
        createMarket();
        String days="""
            {"days":[{"day":0,"enabled":true,"periods":[{"opensAt":"22:00","closesAt":"02:00"}]},
            {"day":1,"enabled":false,"periods":[]},{"day":2,"enabled":false,"periods":[]},
            {"day":3,"enabled":false,"periods":[]},{"day":4,"enabled":false,"periods":[]},
            {"day":5,"enabled":false,"periods":[]},{"day":6,"enabled":false,"periods":[]}]}
            """;
        mvc.perform(put("/api/owner/market/hours").with(user()).contentType(MediaType.APPLICATION_JSON).content(days))
            .andExpect(status().isOk()).andExpect(jsonPath("$.businessHours[0].periods[0].closesAt").value("02:00"));
        String overlap=days.replace("\"day\":1,\"enabled\":false,\"periods\":[]","\"day\":1,\"enabled\":true,\"periods\":[{\"opensAt\":\"01:00\",\"closesAt\":\"03:00\"}]");
        mvc.perform(put("/api/owner/market/hours").with(user()).contentType(MediaType.APPLICATION_JSON).content(overlap)).andExpect(status().isBadRequest());
        assertEquals(1,db.queryForObject("select count(*) from ecofood.market_hours",Integer.class));
    }
    @Test void imageIsPersistedAndInvalidImageRejected() throws Exception {
        createMarket();UUID id=createProduct();
        var buffer=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",buffer);
        mvc.perform(multipart("/api/owner/products/"+id+"/image").file(new MockMultipartFile("file","x.png","image/png",buffer.toByteArray()))
            .with(user()).with(r->{r.setMethod("PUT");return r;})).andExpect(status().isOk());
        mvc.perform(get("/api/products/"+id+"/image")).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(multipart("/api/owner/products/"+id+"/image").file(new MockMultipartFile("file","x.png","image/png","not an image".getBytes()))
            .with(user()).with(r->{r.setMethod("PUT");return r;})).andExpect(status().isBadRequest());
    }
    private byte[] picture(String format,int color) throws Exception {
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.setRGB(0,0,color);
        var out=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image,format,out);
        return out.toByteArray();
    }
    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder marketUpload(byte[] bytes) {
        return multipart(org.springframework.http.HttpMethod.PUT,"/api/owner/market/image")
            .file(new MockMultipartFile("file","photo.png","image/png",bytes));
    }
    @Test void marketPhotoIsPublicAndIncludedInEveryMarketResponse() throws Exception {
        UUID id=createMarket();byte[] bytes=picture("png",0xff6600);
        mvc.perform(get("/api/markets/"+id)).andExpect(jsonPath("$.imageUri").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(marketUpload(bytes).with(user())).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.profile.marketName").value("Mercado Teste"))
            .andExpect(jsonPath("$.businessHours").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.imageUri").value(org.hamcrest.Matchers.startsWith("/api/markets/"+id+"/image?v=")));
        String uri="/api/markets/"+id+"/image?v="+db.queryForObject("select version from ecofood.market_images where market_id=?",UUID.class,id);
        for(String route:new String[]{"/api/owner/market","/api/markets/"+id})
            mvc.perform(get(route).with(user())).andExpect(status().isOk()).andExpect(jsonPath("$.imageUri").value(uri));
        mvc.perform(get("/api/markets")).andExpect(jsonPath("$[0].imageUri").value(uri));
        mvc.perform(get(uri)).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(bytes)).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"));
    }
    @Test void replacingMarketPhotoChangesUrlAndKeepsOnlyNewBytes() throws Exception {
        UUID id=createMarket();
        mvc.perform(marketUpload(picture("png",0)).with(user())).andExpect(status().isOk());
        UUID version=db.queryForObject("select version from ecofood.market_images where market_id=?",UUID.class,id);
        byte[] jpeg=picture("jpeg",0xffffff);
        // Client MIME and filename are untrusted: actual JPEG bytes determine the served type.
        mvc.perform(marketUpload(jpeg).with(user())).andExpect(status().isOk());
        assertNotEquals(version,db.queryForObject("select version from ecofood.market_images where market_id=?",UUID.class,id));
        assertEquals(1,db.queryForObject("select count(*) from ecofood.market_images where market_id=?",Integer.class,id));
        mvc.perform(get("/api/markets/"+id+"/image")).andExpect(content().contentType("image/jpeg")).andExpect(content().bytes(jpeg));
    }
    @Test void marketUploadRejectsBadContentWithoutReplacingExistingPhoto() throws Exception {
        UUID id=createMarket();byte[] original=picture("png",0);
        mvc.perform(marketUpload(original).with(user())).andExpect(status().isOk());
        UUID version=db.queryForObject("select version from ecofood.market_images where market_id=?",UUID.class,id);
        for(byte[] bad:new byte[][]{new byte[0],"<svg/>".getBytes(),picture("gif",0),java.util.Arrays.copyOf(original,33)})
            mvc.perform(marketUpload(bad).with(user())).andExpect(status().isBadRequest());
        mvc.perform(marketUpload(new byte[5*1024*1024+1]).with(user())).andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT,"/api/owner/market/image").with(user()))
            .andExpect(status().isBadRequest());
        assertEquals(version,db.queryForObject("select version from ecofood.market_images where market_id=?",UUID.class,id));
        mvc.perform(get("/api/markets/"+id+"/image")).andExpect(content().bytes(original));
    }
    @Test void marketUploadAcceptsExactlyFiveMegabytes() throws Exception {
        createMarket();
        byte[] bytes=java.util.Arrays.copyOf(picture("png",0),5*1024*1024);
        mvc.perform(marketUpload(bytes).with(user())).andExpect(status().isOk());
    }
    @Test void marketPhotoUsesAuthenticatedOwnerAndCannotTargetAnotherMarket() throws Exception {
        UUID own=createMarket();byte[] original=picture("png",0);
        mvc.perform(marketUpload(original).with(user())).andExpect(status().isOk());
        String otherId=UUID.randomUUID().toString();var other=jwt().jwt(j->j.subject(otherId));
        mvc.perform(marketUpload(original)).andExpect(status().isUnauthorized());
        mvc.perform(marketUpload(original).with(other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/owner/market").with(other).contentType(MediaType.APPLICATION_JSON).content(marketBody("98765432000190")))
            .andExpect(status().isCreated());
        byte[] replacement=picture("jpeg",0xffffff);
        mvc.perform(marketUpload(replacement).param("marketId",own.toString()).param("ownerId",owner).with(other))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(own.toString())));
        mvc.perform(get("/api/markets/"+own+"/image")).andExpect(content().bytes(original));
        UUID otherMarket=db.queryForObject("select id from ecofood.markets where owner_id=?",UUID.class,UUID.fromString(otherId));
        mvc.perform(get("/api/markets/"+otherMarket+"/image")).andExpect(content().bytes(replacement));
    }
    @Test void absentMarketPhotoIs404AndDeletingMarketRemovesImage() throws Exception {
        UUID id=createMarket();
        mvc.perform(get("/api/markets/"+id+"/image")).andExpect(status().isNotFound());
        mvc.perform(get("/api/markets/"+UUID.randomUUID()+"/image")).andExpect(status().isNotFound());
        mvc.perform(marketUpload(picture("png",0)).with(user())).andExpect(status().isOk());
        db.update("delete from ecofood.markets where id=?",id);
        assertEquals(0,db.queryForObject("select count(*) from ecofood.market_images where market_id=?",Integer.class,id));
        mvc.perform(get("/api/markets/"+id+"/image")).andExpect(status().isNotFound());
    }
}
