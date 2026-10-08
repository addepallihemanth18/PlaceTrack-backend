package com.placement.pms;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

@Service
class JwtService {
    @Value("${app.jwt.secret}") String secret;
    @Value("${app.jwt.expiration-ms}") long expiration;

    String create(String email, Role role) {
        return Jwts.builder().subject(email).claim("role", role.name())
            .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    String email(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(token).getPayload().getSubject();
    }
}

@Service
class AppUsers implements UserDetailsService {
    private final UserRepository users;
    AppUsers(UserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = users.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new org.springframework.security.core.userdetails.User(user.email, user.password,
            List.of(new SimpleGrantedAuthority(user.role.name())));
    }
}

@Component
class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final AppUsers users;
    JwtFilter(JwtService jwt, AppUsers users) { this.jwt = jwt; this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
            && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails user = users.loadUserByUsername(jwt.email(header.substring(7)));
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ignored) {
                // Invalid, expired, blank or orphaned tokens leave the request unauthenticated (-> 401).
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
