import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Opt-in real PostgreSQL trigger test. All synthetic auth rows are rolled back; no emails are sent. */
public class UsernamePostgresCheck {
    static void verify(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    static String scalar(Connection c, String sql, UUID id) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            s.setObject(1, id);
            try (var r = s.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }
    static void insert(Connection c, UUID id, String username) throws SQLException {
        try (var s = c.prepareStatement("insert into auth.users(id, email, raw_user_meta_data) values (?, ?, jsonb_build_object('username', cast(? as text)))")) {
            s.setObject(1, id);
            s.setString(2, id + "@example.invalid");
            s.setString(3, username);
            s.executeUpdate();
        }
    }
    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(Path.of("config/local.properties"))) { p.load(r); }
        try (var c = DriverManager.getConnection(p.getProperty("DB_URL"), p.getProperty("DB_USERNAME"), p.getProperty("DB_PASSWORD"))) {
            c.setAutoCommit(false);
            try {
                UUID id = UUID.randomUUID();
                String handle = "check_" + id.toString().replace("-", "").substring(0, 18);
                insert(c, id, "  " + handle.toUpperCase(Locale.ROOT) + "  ");
                verify(handle.equals(scalar(c, "select username from ecofood.user_logins where user_id = ?", id)), "Username normalization failed");
                var savepoint = c.setSavepoint();
                try {
                    insert(c, UUID.randomUUID(), handle);
                    throw new IllegalStateException("Duplicate username was accepted");
                } catch (SQLException expected) {
                    verify("23505".equals(expected.getSQLState()), "Unexpected duplicate error");
                    c.rollback(savepoint);
                }
                savepoint = c.setSavepoint();
                try {
                    insert(c, UUID.randomUUID(), "bad username!");
                    throw new IllegalStateException("Invalid username was accepted");
                } catch (SQLException expected) {
                    verify("23514".equals(expected.getSQLState()), "Unexpected validation error");
                    c.rollback(savepoint);
                }
                try (var s = c.prepareStatement("update auth.users set email = 'updated@example.invalid' where id = ?")) {
                    s.setObject(1, id); s.executeUpdate();
                }
                verify("updated@example.invalid".equals(scalar(c, "select email from ecofood.user_logins where user_id = ?", id)), "Email synchronization failed");
                try (var s = c.prepareStatement("update auth.users set raw_user_meta_data = jsonb_build_object('username', cast(? as text)) where id = ?")) {
                    s.setString(1, handle + "_2"); s.setObject(2, id); s.executeUpdate();
                }
                verify((handle + "_2").equals(scalar(c, "select username from ecofood.user_logins where user_id = ?", id)), "Username synchronization failed");
                try (var s = c.prepareStatement("update auth.users set deleted_at = now() where id = ?")) {
                    s.setObject(1, id); s.executeUpdate();
                }
                verify(scalar(c, "select username from ecofood.user_logins where user_id = ?", id) == null, "Soft delete did not remove login mapping");
                System.out.println("PASS: normalization, uniqueness, format, email/username synchronization, soft deletion");
            } finally {
                c.rollback();
                System.out.println("Synthetic test data rolled back; no email sent.");
            }
            try (var s = c.createStatement(); var r = s.executeQuery("select count(*), count(username) from ecofood.user_logins")) {
                r.next();
                System.out.println("Existing accounts=" + r.getInt(1) + "; accounts with unique valid username=" + r.getInt(2));
            }
        } catch (SQLException e) {
            System.err.println("Database test failed; SQLState=" + e.getSQLState());
            System.exit(1);
        }
    }
}
