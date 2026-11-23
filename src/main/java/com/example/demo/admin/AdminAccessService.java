package com.example.demo.admin;

import java.util.*;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AdminAccessService {
    private final JdbcTemplate db;
    private final AdminAllowlist allowlist;
    private final Clock clock;
    public AdminAccessService(JdbcTemplate db,AdminAllowlist allowlist,Clock clock) {this.db=db;this.allowlist=allowlist;this.clock=clock;}
    public record Access(boolean admin,boolean suspended) {}
    public void ensure(UUID id) { db.update("INSERT INTO ecofood.user_access(user_id) VALUES (?) ON CONFLICT DO NOTHING",id); }
    @Transactional public Access synchronize(TrustedIdentityClient.Identity identity) {
        UUID id=identity.id();ensure(id);
        var old=db.queryForMap("SELECT * FROM ecofood.user_access WHERE user_id=? FOR UPDATE",id);
        boolean admin=identity.email_confirmed_at()!=null && allowlist.contains(identity.email());
        if((boolean)old.get("is_admin")!=admin) audit(id,id,admin?"ADMIN_GRANTED":"ADMIN_REVOKED","Regra de allowlist verificada no servidor",String.valueOf(old.get("is_admin")),String.valueOf(admin));
        db.update("UPDATE ecofood.user_access SET is_admin=?,admin_email_hash=? WHERE user_id=?",admin,admin?AdminAllowlist.hash(identity.email()):null,id);
        return new Access(admin,(boolean)old.get("suspended"));
    }
    @Transactional public void revokeRemoved() {
        var ids=db.query("SELECT user_id,admin_email_hash FROM ecofood.user_access WHERE is_admin=true FOR UPDATE",
            (r,n)->Map.entry(r.getObject(1,UUID.class),Objects.toString(r.getString(2),"")));
        for(var row:ids) if(!allowlist.hashes().contains(row.getValue())) {
            db.update("UPDATE ecofood.user_access SET is_admin=false,admin_email_hash=null WHERE user_id=?",row.getKey());
            audit(null,row.getKey(),"ADMIN_REVOKED","Endereço removido da allowlist", "true","false");
        }
    }
    @Transactional public void rateLimit(UUID id) {
        var now=clock.instant();var cutoff=Timestamp.from(now.minusSeconds(60));
        int n=db.update("""
            UPDATE ecofood.user_access SET
            rate_count=CASE WHEN rate_window IS NULL OR rate_window<=? THEN 1 ELSE rate_count+1 END,
            rate_window=CASE WHEN rate_window IS NULL OR rate_window<=? THEN ? ELSE rate_window END
            WHERE user_id=? AND (rate_window IS NULL OR rate_window<=? OR rate_count<120)
            """,cutoff,cutoff,Timestamp.from(now),id,cutoff);
        if(n==0) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Limite administrativo atingido. Aguarde um minuto.");
    }
    public void audit(UUID actor,UUID target,String action,String reason,String before,String after) {
        db.update("INSERT INTO ecofood.admin_audit(id,actor_id,target_id,action,reason,previous_value,new_value) VALUES (?,?,?,?,?,?,?)",
            UUID.randomUUID(),actor,target,action,reason,before,after);
    }
}
