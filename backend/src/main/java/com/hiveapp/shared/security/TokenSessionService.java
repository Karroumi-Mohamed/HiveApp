package com.hiveapp.shared.security;

import com.hiveapp.shared.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class TokenSessionService {

    private static final String INVALID_REFRESH_TOKEN = "Invalid, expired, or already used refresh token";

    private final JwtTokenProvider jwtTokenProvider;
    private final ConcurrentMap<UUID, RefreshSession> activeRefreshTokens = new ConcurrentHashMap<>();

    public IssuedTokens issue(UUID userId, TokenAudience audience) {
        removeExpiredSessions();
        UUID tokenId = UUID.randomUUID();
        String accessToken = jwtTokenProvider.generateAccessToken(userId, audience);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId, audience, tokenId);
        activeRefreshTokens.put(tokenId, new RefreshSession(
                userId,
                audience,
                Instant.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpiration())));
        return new IssuedTokens(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }

    public RefreshTokenIdentity consume(String refreshToken, TokenAudience expectedAudience) {
        ParsedRefreshToken parsed = parse(refreshToken, expectedAudience);
        RefreshSession session = activeRefreshTokens.get(parsed.tokenId());
        if (session == null
                || !session.userId().equals(parsed.userId())
                || session.audience() != expectedAudience
                || !activeRefreshTokens.remove(parsed.tokenId(), session)) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }
        return new RefreshTokenIdentity(parsed.userId(), expectedAudience);
    }

    public void revoke(String refreshToken, TokenAudience expectedAudience) {
        consume(refreshToken, expectedAudience);
    }

    private void removeExpiredSessions() {
        Instant now = Instant.now();
        activeRefreshTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private ParsedRefreshToken parse(String token, TokenAudience expectedAudience) {
        try {
            if (!jwtTokenProvider.validateToken(token)) {
                throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
            }
            Claims claims = jwtTokenProvider.getClaimsFromToken(token);
            if (!jwtTokenProvider.hasPurpose(claims, expectedAudience, TokenUse.REFRESH)
                    || claims.getId() == null) {
                throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
            }
            return new ParsedRefreshToken(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.getId()));
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }
    }

    private record RefreshSession(UUID userId, TokenAudience audience, Instant expiresAt) {
    }

    private record ParsedRefreshToken(UUID userId, UUID tokenId) {
    }
}
