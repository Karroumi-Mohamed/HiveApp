package com.hiveapp.shared.security;

import com.hiveapp.shared.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, TokenAudience audience) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("tokenType", audience.name())
                .claim("tokenUse", TokenUse.ACCESS.name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtProperties.getAccessTokenExpiration().toMillis()))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(UUID userId, TokenAudience audience, UUID tokenId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .id(tokenId.toString())
                .claim("tokenType", audience.name())
                .claim("tokenUse", TokenUse.REFRESH.name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtProperties.getRefreshTokenExpiration().toMillis()))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateInitialAccessToken(UUID userId, UUID tokenId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .id(tokenId.toString())
                .claim("tokenType", TokenAudience.CLIENT.name())
                .claim("tokenUse", TokenUse.INITIAL_ACCESS.name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtProperties.getAccessTokenExpiration().toMillis()))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }

    @NonNull
    public UUID getUserIdFromToken(String token) {
        String subject = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return UUID.fromString(subject);
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean hasPurpose(Claims claims, TokenAudience audience, TokenUse tokenUse) {
        return audience.name().equals(claims.get("tokenType", String.class))
                && tokenUse.name().equals(claims.get("tokenUse", String.class));
    }

    public long getAccessTokenExpiration() {
        return jwtProperties.getAccessTokenExpiration().toSeconds();
    }

    public long getRefreshTokenExpiration() {
        return jwtProperties.getRefreshTokenExpiration().toSeconds();
    }
}
