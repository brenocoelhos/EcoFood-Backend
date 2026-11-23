package com.example.demo.catalog;

import static com.example.demo.catalog.CatalogDtos.*;
import com.example.demo.catalog.CatalogDtos.Period;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
    private final JdbcTemplate db;
    public CatalogRepository(JdbcTemplate db) { this.db = db; }
    public record MarketRow(UUID id, UUID ownerId, MarketInput profile, boolean hoursConfigured) {}
    public record ProductRow(OwnerProduct product, UUID marketId, String marketName, String location) {}
    public record CatalogSource(String contentType,byte[] content) {}
    public Optional<CatalogSource> catalogSource(UUID id) {
        // One statement captures existence and image in the same snapshot, even during source deletion/replacement.
        return db.query("SELECT i.content_type,i.content FROM ecofood.products p LEFT JOIN ecofood.product_images i ON i.product_id=p.id WHERE p.id=?",
            (r,n)->new CatalogSource(r.getString(1),r.getBytes(2)),id).stream().findFirst();
    }
    public List<CatalogProduct> suggestions(String query,int limit) {
        String escaped=query.replace("!","!!").replace("%","!%").replace("_","!_");
        return db.query("""
            SELECT p.id,p.name,p.category_id,m.market_name,i.product_id AS image_id
            FROM ecofood.products p JOIN ecofood.markets m ON m.id=p.market_id
            LEFT JOIN ecofood.product_images i ON i.product_id=p.id
            WHERE p.search_name LIKE ? ESCAPE '!'
            ORDER BY CASE WHEN p.search_name=? THEN 0 WHEN p.search_name LIKE ? ESCAPE '!' THEN 1 ELSE 2 END,
                     p.search_name,p.id
            LIMIT ?
            """,(r,n)->new CatalogProduct(r.getObject("id",UUID.class),r.getString("name"),r.getString("category_id"),
                r.getObject("image_id")==null?null:"/api/owner/product-catalog/"+r.getObject("id",UUID.class)+"/image",r.getString("market_name")),
                "%"+escaped+"%",query,escaped+"%",limit);
    }
    public int expireProducts(LocalDate today) {
        return db.update("UPDATE ecofood.products SET is_active=false WHERE is_active=true AND expiration_date<?",today);
    }
    public void expireProduct(UUID id,LocalDate today) {
        db.update("UPDATE ecofood.products SET is_active=false WHERE id=? AND is_active=true AND expiration_date<?",id,today);
    }
    private static final String PRODUCT_SELECT = """
        SELECT p.*, m.market_name, m.city, m.state,
          CASE WHEN i.product_id IS NULL THEN false ELSE true END AS has_image
        FROM ecofood.products p JOIN ecofood.markets m ON m.id=p.market_id
        LEFT JOIN ecofood.product_images i ON i.product_id=p.id
        """;
    private static final String AVAILABLE = " p.is_active=true AND p.stock_quantity>0 AND p.expiration_date>=? ";
    public List<Category> categories() {
        return db.query("SELECT id,name FROM ecofood.categories ORDER BY id", (r,n) -> new Category(r.getString(1),r.getString(2)));
    }
    public boolean categoryExists(String id) {
        return db.queryForObject("SELECT count(*) FROM ecofood.categories WHERE id=?", Integer.class, id)>0;
    }
    public Optional<MarketRow> ownerMarket(UUID ownerId, boolean lock) {
        return db.query("SELECT * FROM ecofood.markets WHERE owner_id=?" + (lock?" FOR UPDATE":""), this::marketRow, ownerId).stream().findFirst();
    }
    public Optional<MarketRow> market(UUID id) {
        return db.query("SELECT * FROM ecofood.markets WHERE id=?", this::marketRow,id).stream().findFirst();
    }
    public List<MarketRow> markets(int limit,int offset) {
        return db.query("SELECT * FROM ecofood.markets ORDER BY market_name,id LIMIT ? OFFSET ?", this::marketRow,limit,offset);
    }
    public void createMarket(UUID id, UUID owner, MarketInput p) {
        db.update("""
            INSERT INTO ecofood.markets(id,owner_id,market_name,market_phone,document,postal_code,state,city,
            neighborhood,address,address_number,complement,name,last_name,username,email,phone,referral_source)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,owner,p.marketName().trim(),p.marketPhone().trim(),p.document().replaceAll("\\D",""),
            p.postalCode(),p.state(),p.city().trim(),p.neighborhood().trim(),p.address().trim(),p.addressNumber(),
            empty(p.complement()),p.name().trim(),p.lastName().trim(),p.username().trim(),p.email().trim(),p.phone(),empty(p.referralSource()));
    }
    public void updateMarket(UUID id,MarketInput p) {
        db.update("""
            UPDATE ecofood.markets SET market_name=?,market_phone=?,document=?,postal_code=?,state=?,city=?,
            neighborhood=?,address=?,address_number=?,complement=?,name=?,last_name=?,username=?,email=?,phone=?,
            referral_source=?,updated_at=CURRENT_TIMESTAMP WHERE id=?
            """,p.marketName().trim(),p.marketPhone().trim(),p.document().replaceAll("\\D",""),p.postalCode(),p.state(),
            p.city().trim(),p.neighborhood().trim(),p.address().trim(),p.addressNumber(),empty(p.complement()),
            p.name().trim(),p.lastName().trim(),p.username().trim(),p.email().trim(),p.phone(),empty(p.referralSource()),id);
    }
    public List<DayHours> hours(MarketRow m) {
        if (!m.hoursConfigured()) return null;
        List<DayHours> days = new ArrayList<>();
        record Slot(int day, Period period) {}
        var slots=db.query("SELECT day_of_week,opens_at,closes_at FROM ecofood.market_hours WHERE market_id=? ORDER BY day_of_week,opens_at",
            (r,n)->new Slot(r.getInt(1),new Period(r.getTime(2).toLocalTime().toString(),r.getTime(3).toLocalTime().toString())),m.id());
        for (int day=0;day<7;day++) {
            int currentDay=day;
            var periods=slots.stream().filter(s->s.day()==currentDay).map(Slot::period).toList();
            days.add(new DayHours(day,!periods.isEmpty(),periods));
        }
        return days;
    }
    public void saveHours(UUID marketId,List<DayHours> days) {
        db.update("DELETE FROM ecofood.market_hours WHERE market_id=?",marketId);
        for (var day:days) if (day.enabled()) for(var period:day.periods())
            db.update("INSERT INTO ecofood.market_hours(id,market_id,day_of_week,opens_at,closes_at) VALUES (?,?,?,?,?)",
                UUID.randomUUID(),marketId,day.day(),Time.valueOf(LocalTime.parse(period.opensAt())),Time.valueOf(LocalTime.parse(period.closesAt())));
        db.update("UPDATE ecofood.markets SET hours_configured=true,updated_at=CURRENT_TIMESTAMP WHERE id=?",marketId);
    }
    public List<ProductRow> ownerProducts(UUID marketId,int limit,int offset) {
        return db.query(PRODUCT_SELECT+" WHERE p.market_id=? ORDER BY p.created_at DESC,p.id LIMIT ? OFFSET ?",this::productRow,marketId,limit,offset);
    }
    public Optional<ProductRow> product(UUID id) {
        return db.query(PRODUCT_SELECT+" WHERE p.id=?",this::productRow,id).stream().findFirst();
    }
    public List<ProductRow> publicProducts(UUID marketId,String query,String category,LocalDate today,int limit,int offset) {
        String sql=PRODUCT_SELECT+" WHERE "+AVAILABLE;
        List<Object> args=new ArrayList<>(List.of(today));
        if(marketId!=null) {sql+=" AND p.market_id=?";args.add(marketId);}
        if(category!=null && !category.isBlank()) {sql+=" AND p.category_id=?";args.add(category);}
        if(query!=null && !query.isBlank()) {
            sql+=" AND p.search_name LIKE ? ESCAPE '!'";
            args.add("%"+query.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        }
        sql+=" ORDER BY p.expiration_date,p.id LIMIT ? OFFSET ?";args.add(limit);args.add(offset);
        return db.query(sql,this::productRow,args.toArray());
    }
    public int[] marketStats(UUID id,LocalDate today) {
        return db.queryForObject("SELECT count(*),coalesce(max(round((1-offer_price/original_price)*100)),0) FROM ecofood.products p WHERE p.market_id=? AND "+AVAILABLE,
            (r,n)->new int[]{r.getInt(1),r.getInt(2)},id,today);
    }
    public void createProduct(UUID id,UUID marketId,ProductInput p,String searchName) {
        db.update("""
            INSERT INTO ecofood.products(id,market_id,category_id,name,search_name,manufacture_date,expiration_date,original_price,offer_price,stock_quantity)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """,id,marketId,p.categoryId(),p.name().trim(),searchName,p.manufactureDate(),p.expirationDate(),p.originalPrice(),p.offerPrice(),p.stockQuantity());
    }
    public void updateProduct(UUID id,ProductInput p,String searchName) {
        db.update("""
            UPDATE ecofood.products SET category_id=?,name=?,search_name=?,manufacture_date=?,expiration_date=?,original_price=?,
            offer_price=?,stock_quantity=?,updated_at=CURRENT_TIMESTAMP WHERE id=?
            """,p.categoryId(),p.name().trim(),searchName,p.manufactureDate(),p.expirationDate(),p.originalPrice(),p.offerPrice(),p.stockQuantity(),id);
    }
    public void active(UUID id,boolean active) { db.update("UPDATE ecofood.products SET is_active=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",active,id); }
    public void deleteProduct(UUID id) {db.update("DELETE FROM ecofood.products WHERE id=?",id);}
    public record ProductImage(String contentType,byte[] content) {}
    public String marketImageUri(UUID id) {
        return db.query("SELECT version FROM ecofood.market_images WHERE market_id=?",
            (r,n)->"/api/markets/"+id+"/image?v="+r.getObject(1,UUID.class),id).stream().findFirst().orElse(null);
    }
    public Optional<ProductImage> marketImage(UUID id) {
        return db.query("SELECT content_type,content FROM ecofood.market_images WHERE market_id=?",
            (r,n)->new ProductImage(r.getString(1),r.getBytes(2)),id).stream().findFirst();
    }
    // Caller holds the market row lock; replacement and new URL version commit atomically.
    public void saveMarketImage(UUID id,String type,byte[] bytes) {
        db.update("DELETE FROM ecofood.market_images WHERE market_id=?",id);
        db.update("INSERT INTO ecofood.market_images(market_id,version,content_type,content) VALUES (?,?,?,?)",
            id,UUID.randomUUID(),type,bytes);
        db.update("UPDATE ecofood.markets SET updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
    }
    public Optional<ProductImage> image(UUID id) {
        return db.query("SELECT content_type,content FROM ecofood.product_images WHERE product_id=?",(r,n)->new ProductImage(r.getString(1),r.getBytes(2)),id).stream().findFirst();
    }
    public void saveImage(UUID id,String type,byte[] bytes) {
        db.update("DELETE FROM ecofood.product_images WHERE product_id=?",id);
        db.update("INSERT INTO ecofood.product_images(product_id,content_type,content) VALUES (?,?,?)",id,type,bytes);
    }
    private ProductRow productRow(ResultSet r,int n) throws SQLException {
        UUID id=r.getObject("id",UUID.class);
        return new ProductRow(new OwnerProduct(id,r.getBoolean("is_active"),r.getString("name"),r.getString("category_id"),
            r.getObject("manufacture_date",LocalDate.class),r.getObject("expiration_date",LocalDate.class),r.getBigDecimal("original_price"),
            r.getBigDecimal("offer_price"),r.getInt("stock_quantity"),r.getBoolean("has_image")?"/api/products/"+id+"/image":null),
            r.getObject("market_id",UUID.class),r.getString("market_name"),r.getString("city")+" · "+r.getString("state"));
    }
    private MarketRow marketRow(ResultSet r,int n) throws SQLException {
        return new MarketRow(r.getObject("id",UUID.class),r.getObject("owner_id",UUID.class),new MarketInput(
            r.getString("market_name"),r.getString("market_phone"),r.getString("document"),r.getString("postal_code"),r.getString("state"),
            r.getString("city"),r.getString("neighborhood"),r.getString("address"),r.getString("address_number"),r.getString("complement"),
            r.getString("name"),r.getString("last_name"),r.getString("username"),r.getString("email"),r.getString("phone"),r.getString("referral_source")),r.getBoolean("hours_configured"));
    }
    private String empty(String s) {return s==null?"":s.trim();}
}
