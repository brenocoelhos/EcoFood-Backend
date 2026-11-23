package com.example.demo.admin;

import static com.example.demo.admin.AdminDtos.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly=true)
public class AdminService {
    private final JdbcTemplate db;
    private final AdminAccessService access;
    private final AdminAllowlist allowlist;
    private final Clock clock;
    private static final ZoneId ZONE=ZoneId.of("America/Sao_Paulo");
    public AdminService(JdbcTemplate db,AdminAccessService access,AdminAllowlist allowlist,Clock clock) {this.db=db;this.access=access;this.allowlist=allowlist;this.clock=clock;}
    private LocalDate today() {return LocalDate.now(clock.withZone(ZONE));}
    private ResponseStatusException missing() {return new ResponseStatusException(NOT_FOUND,"Registro não encontrado.");}
    private Instant instant(ResultSet r,String column) throws SQLException {return r.getTimestamp(column).toInstant();}
    private static String pattern(String q) {return "%"+q.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    private static void option(String value,String... allowed) {
        if(value!=null && !List.of(allowed).contains(value)) throw new ResponseStatusException(BAD_REQUEST,"Filtro inválido.");
    }
    public Overview overview() {
        return new Overview(db.queryForObject("SELECT count(*) FROM ecofood.markets",Long.class),
            db.queryForObject("SELECT count(*) FROM ecofood.products p WHERE p.is_active=true AND p.expiration_date>=? AND p.stock_quantity>0",Long.class,today()),
            db.queryForObject("SELECT count(*) FROM ecofood.admin_users",Long.class));
    }
    private static final String MARKET_FROM=" FROM ecofood.markets m LEFT JOIN ecofood.user_logins u ON u.user_id=m.owner_id LEFT JOIN ecofood.user_access a ON a.user_id=m.owner_id ";
    private static final String MARKET_FIELDS="m.id,m.market_name,m.owner_id,u.username,m.city,m.state,m.created_at,coalesce(a.suspended,false) AS suspended,(SELECT count(*) FROM ecofood.products p WHERE p.market_id=m.id) AS products";
    private final RowMapper<Market> marketMapper=(r,n)->new Market(r.getObject("id",UUID.class),r.getString("market_name"),r.getObject("owner_id",UUID.class),r.getString("username"),r.getString("city"),r.getString("state"),r.getBoolean("suspended")?"suspended":"registered",r.getLong("products"),instant(r,"created_at"));
    public Page<Market> markets(String q,String state,String status,int page,int size) {
        option(status,"registered","suspended");
        String where=" WHERE (lower(m.market_name) LIKE ? ESCAPE '!' OR lower(m.city) LIKE ? ESCAPE '!')";
        List<Object> args=new ArrayList<>(List.of(pattern(q),pattern(q)));
        if(state!=null) {where+=" AND m.state=?";args.add(state);}
        if(status!=null) {where+=" AND coalesce(a.suspended,false)=?";args.add(status.equals("suspended"));}
        return page("SELECT "+MARKET_FIELDS,MARKET_FROM,where,"m.created_at DESC,m.id",args,page,size,marketMapper);
    }
    public Market market(UUID id) {return db.query("SELECT "+MARKET_FIELDS+MARKET_FROM+" WHERE m.id=?",marketMapper,id).stream().findFirst().orElseThrow(this::missing);}
    private static final String PRODUCT_FROM=" FROM ecofood.products p JOIN ecofood.markets m ON m.id=p.market_id LEFT JOIN ecofood.product_images i ON i.product_id=p.id ";
    private static final String PRODUCT_FIELDS="p.id,p.name,p.market_id,m.market_name,p.expiration_date,p.is_active,p.stock_quantity,p.created_at,i.product_id AS image_id";
    private final RowMapper<Product> productMapper=(r,n)->new Product(r.getObject("id",UUID.class),r.getString("name"),r.getObject("market_id",UUID.class),r.getString("market_name"),r.getObject("expiration_date",LocalDate.class),r.getBoolean("is_active"),r.getInt("stock_quantity"),r.getObject("image_id")==null?null:"/api/admin/products/"+r.getObject("id",UUID.class)+"/image",instant(r,"created_at"));
    public Page<Product> products(String q,UUID marketId,String status,int page,int size) {
        option(status,"active","inactive","expired");
        String where=" WHERE p.search_name LIKE ? ESCAPE '!'";
        List<Object> args=new ArrayList<>(List.of(pattern(java.text.Normalizer.normalize(q,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}",""))));
        if(marketId!=null) {where+=" AND p.market_id=?";args.add(marketId);}
        if("expired".equals(status)) {where+=" AND p.expiration_date<?";args.add(today());}
        if("active".equals(status)) {where+=" AND p.is_active=true AND p.expiration_date>=?";args.add(today());}
        if("inactive".equals(status)) {where+=" AND (p.is_active=false OR p.expiration_date<?)";args.add(today());}
        return page("SELECT "+PRODUCT_FIELDS,PRODUCT_FROM,where,"p.created_at DESC,p.id",args,page,size,productMapper);
    }
    public Product product(UUID id) {return db.query("SELECT "+PRODUCT_FIELDS+PRODUCT_FROM+" WHERE p.id=?",productMapper,id).stream().findFirst().orElseThrow(this::missing);}
    public com.example.demo.catalog.CatalogRepository.ProductImage image(UUID id) {
        return db.query("SELECT content_type,content FROM ecofood.product_images WHERE product_id=?",(r,n)->new com.example.demo.catalog.CatalogRepository.ProductImage(r.getString(1),r.getBytes(2)),id).stream().findFirst().orElseThrow(this::missing);
    }
    private static final String ROLE="CASE WHEN coalesce(a.is_admin,false) THEN 'admin' WHEN EXISTS(SELECT 1 FROM ecofood.markets m WHERE m.owner_id=u.id) THEN 'marketOwner' ELSE 'customer' END";
    private static final String USER_FROM=" FROM ecofood.admin_users u LEFT JOIN ecofood.user_logins l ON l.user_id=u.id LEFT JOIN ecofood.user_access a ON a.user_id=u.id ";
    private final RowMapper<User> userMapper=(r,n)->new User(r.getObject("id",UUID.class),r.getString("username"),r.getString("role"),r.getBoolean("suspended"),instant(r,"created_at"));
    public Page<User> users(String q,String role,Boolean suspended,int page,int size) {
        option(role,"admin","marketOwner","customer");
        String where=" WHERE (lower(coalesce(l.username,'')) LIKE ? ESCAPE '!' OR lower(cast(u.id AS varchar)) LIKE ? ESCAPE '!')";
        List<Object> args=new ArrayList<>(List.of(pattern(q),pattern(q)));
        if(role!=null) {where+=" AND "+ROLE+"=?";args.add(role);}
        if(suspended!=null) {where+=" AND coalesce(a.suspended,false)=?";args.add(suspended);}
        return page("SELECT u.id,l.username,u.created_at,coalesce(a.suspended,false) AS suspended,"+ROLE+" AS role",USER_FROM,where,"u.created_at DESC,u.id",args,page,size,userMapper);
    }
    public User user(UUID id) {return db.query("SELECT u.id,l.username,u.created_at,coalesce(a.suspended,false) AS suspended,"+ROLE+" AS role"+USER_FROM+" WHERE u.id=?",userMapper,id).stream().findFirst().orElseThrow(this::missing);}
    private void lockActor(UUID actor) {
        var rows=db.queryForList("SELECT is_admin,suspended,admin_email_hash FROM ecofood.user_access WHERE user_id=? FOR UPDATE",actor);
        if(rows.isEmpty() || !(boolean)rows.get(0).get("is_admin") || (boolean)rows.get(0).get("suspended") || !allowlist.hashes().contains(rows.get(0).get("admin_email_hash")))
            throw new ResponseStatusException(FORBIDDEN,"Acesso administrativo não autorizado.");
    }
    @Transactional public Product moderate(UUID actor,UUID id,ProductStatus input) {
        lockActor(actor);
        var rows=db.queryForList("SELECT is_active,expiration_date FROM ecofood.products WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw missing();
        var row=rows.get(0);
        if(input.isActive() && ((java.sql.Date)row.get("expiration_date")).toLocalDate().isBefore(today()))
            throw new ResponseStatusException(BAD_REQUEST,"Produto vencido não pode ser reativado.");
        db.update("UPDATE ecofood.products SET is_active=? WHERE id=?",input.isActive(),id);
        access.audit(actor,id,"PRODUCT_STATUS",input.reason().strip(),row.get("is_active").toString(),input.isActive().toString());
        return product(id);
    }
    @Transactional public User suspension(UUID actor,UUID id,Suspension input) {
        lockActor(actor);
        if(actor.equals(id)) throw new ResponseStatusException(CONFLICT,"Não é permitido suspender a própria conta.");
        user(id);access.ensure(id);
        boolean previous=db.queryForObject("SELECT suspended FROM ecofood.user_access WHERE user_id=? FOR UPDATE",Boolean.class,id);
        db.update("UPDATE ecofood.user_access SET suspended=? WHERE user_id=?",input.suspended(),id);
        access.audit(actor,id,input.suspended()?"USER_SUSPENDED":"USER_RESTORED",input.reason().strip(),String.valueOf(previous),input.suspended().toString());
        return user(id);
    }
    public Page<Audit> audit(UUID actorId,UUID targetId,String action,int page,int size) {
        String where=" WHERE 1=1";List<Object> args=new ArrayList<>();
        if(actorId!=null) {where+=" AND actor_id=?";args.add(actorId);}
        if(targetId!=null) {where+=" AND target_id=?";args.add(targetId);}
        if(action!=null) {where+=" AND action=?";args.add(action);}
        return page("SELECT *"," FROM ecofood.admin_audit",where,"occurred_at DESC,id",args,page,size,
            (r,n)->new Audit(r.getObject("id",UUID.class),r.getObject("actor_id",UUID.class),r.getObject("target_id",UUID.class),r.getString("action"),instant(r,"occurred_at"),r.getString("reason"),r.getString("previous_value"),r.getString("new_value")));
    }
    private <T> Page<T> page(String select,String from,String where,String order,List<Object> args,int page,int size,RowMapper<T> mapper) {
        long total=db.queryForObject("SELECT count(*)"+from+where,Long.class,args.toArray());
        var paged=new ArrayList<>(args);paged.add(size);paged.add((long)page*size);
        return new Page<>(db.query(select+from+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",mapper,paged.toArray()),total,page,size);
    }
    public Report report(LocalDate from,LocalDate to,int page,int size) {
        long days=ChronoUnit.DAYS.between(from,to)+1;
        if(days<1 || days>366) throw new ResponseStatusException(BAD_REQUEST,"Informe uma janela de 1 a 366 dias.");
        var start=Timestamp.from(from.atStartOfDay(ZONE).toInstant());var end=Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant());
        var counts=db.query("SELECT cast(occurred_at AT TIME ZONE 'America/Sao_Paulo' AS date) AS report_date,event_type,count(*) AS total FROM ecofood.catalog_events WHERE occurred_at>=? AND occurred_at<? GROUP BY cast(occurred_at AT TIME ZONE 'America/Sao_Paulo' AS date),event_type",
            (r,n)->new Object[]{r.getObject("report_date",LocalDate.class),r.getString("event_type"),r.getLong("total")},start,end);
        Map<LocalDate,long[]> grouped=new HashMap<>();long[] totals=new long[4];
        for(var row:counts) {
            int index=switch((String)row[1]) {case "PRODUCT_CREATED"->0;case "PRODUCT_SAVED"->1;case "PRODUCT_DEACTIVATED"->2;case "MARKET_CREATED"->3;default->-1;};
            if(index>=0) {grouped.computeIfAbsent((LocalDate)row[0],k->new long[4])[index]+=(long)row[2];totals[index]+=(long)row[2];}
        }
        List<Series> series=new ArrayList<>();
        for(long offset=(long)page*size;offset<Math.min(days,(long)(page+1)*size);offset++) {
            LocalDate date=from.plusDays(offset);series.add(new Series(date,totals(grouped.getOrDefault(date,new long[4]))));
        }
        Instant since=db.queryForObject("SELECT activity_tracking_since FROM ecofood.reporting_metadata WHERE id=1",(r,n)->r.getTimestamp(1).toInstant());
        return new Report(from,to,ZONE.getId(),since,totals(totals),new Page<>(series,days,page,size));
    }
    private Totals totals(long[] n) {return new Totals(n[0],n[1],n[2],n[3]);}
}
