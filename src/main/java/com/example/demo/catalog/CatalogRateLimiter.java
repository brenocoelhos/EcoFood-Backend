package com.example.demo.catalog;

import java.time.Clock;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Shared across AWS instances; only the verified owner's persistent market is counted. */
@Service
public class CatalogRateLimiter {
    private final JdbcTemplate db;
    private final Clock clock;
    public CatalogRateLimiter(JdbcTemplate db, Clock clock) { this.db=db; this.clock=clock; }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void check(UUID owner) {
        Timestamp now=Timestamp.from(clock.instant()), cutoff=Timestamp.from(clock.instant().minusSeconds(60));
        int updated=db.update("""
            UPDATE ecofood.markets SET
              catalog_requests=CASE WHEN catalog_window IS NULL OR catalog_window<=? THEN 1 ELSE catalog_requests+1 END,
              catalog_window=CASE WHEN catalog_window IS NULL OR catalog_window<=? THEN ? ELSE catalog_window END
            WHERE owner_id=? AND (catalog_window IS NULL OR catalog_window<=? OR catalog_requests<120)
            """,cutoff,cutoff,now,owner,cutoff);
        if(updated==0) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
            "Limite do catálogo atingido. Aguarde um minuto antes de tentar novamente.");
    }
}
