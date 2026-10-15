package com.example.demo.auth;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UsernameRepository {
    private final JdbcTemplate jdbc;
    public UsernameRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String emailFor(String username) {
        return jdbc.query("select email from ecofood.user_logins where username = ?",
                (rs, row) -> rs.getString(1), username).stream().filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }
    public boolean available(String username) {
        return jdbc.queryForObject("select count(*) from ecofood.user_logins where username = ?",
                Integer.class, username) == 0;
    }
    public String usernameFor(UUID id) {
        return jdbc.query("select username from ecofood.user_logins where user_id = ?",
                (rs, row) -> rs.getString(1), id).stream().filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }
}
