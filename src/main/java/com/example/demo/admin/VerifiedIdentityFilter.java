package com.example.demo.admin;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

public class VerifiedIdentityFilter extends OncePerRequestFilter {
    private final TrustedIdentityClient identities;
    private final AdminAccessService access;
    private final AdminAllowlist allowlist;
    public VerifiedIdentityFilter(TrustedIdentityClient identities,AdminAccessService access,AdminAllowlist allowlist) {
        this.identities=identities;this.access=access;this.allowlist=allowlist;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth instanceof JwtAuthenticationToken token) {
            try {
                var identity=identities.verify(token.getToken());
                // No role elevation, profile disclosure or admin operations over cleartext transport.
                if(!request.isSecure() && (request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/admin") ||
                        (identity.email_confirmed_at()!=null && allowlist.contains(identity.email())))) {
                    reject(response,426,"Use HTTPS para acesso administrativo.");return;
                }
                var state=access.synchronize(identity);
                if(state.suspended()) {reject(response,403,"Conta suspensa.");return;}
                var verified=new JwtAuthenticationToken(token.getToken(),state.admin()?List.of(new SimpleGrantedAuthority("ROLE_ADMIN")):List.of());
                SecurityContextHolder.getContext().setAuthentication(verified);
                request.setAttribute("verifiedIdentity",identity);
                if(request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/admin") && state.admin()) access.rateLimit(identity.id());
            } catch(ResponseStatusException e) {
                SecurityContextHolder.clearContext();reject(response,e.getStatusCode().value(),e.getReason());return;
            } catch(RuntimeException e) {
                SecurityContextHolder.clearContext();reject(response,503,"Não foi possível verificar o acesso.");return;
            }
        }
        chain.doFilter(request,response);
    }
    private void reject(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status);response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control","no-store");
        response.getWriter().write("{\"message\":\""+message+"\"}");
    }
}
