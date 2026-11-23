package com.example.demo.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AdminAllowlist {
    private final Set<String> hashes;
    public AdminAllowlist(@Value("${ADMIN_EMAILS:}") String emails) {
        hashes=Arrays.stream(emails.split(",")).map(AdminAllowlist::normalize).filter(s->!s.isEmpty())
            .map(AdminAllowlist::hash).collect(Collectors.toUnmodifiableSet());
    }
    public boolean contains(String email) { return email!=null && hashes.contains(hash(normalize(email))); }
    public Set<String> hashes() { return hashes; }
    public static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    public static String hash(String email) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalize(email).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException("Digest unavailable");}
    }
}
