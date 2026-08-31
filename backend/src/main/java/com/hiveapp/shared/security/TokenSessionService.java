package com.hiveapp.shared.security;

import com.hiveapp.shared.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class TokenSessionService {

    private static final String INVALID_REFRESH_TOKEN = "Invalid, expired, or already used refresh token";

    private final JwtTokenProvider jwtTokenProvider;
    private final ConcurrentMap<UUID, AccessSession> activeAccessTokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, RefreshSession> activeRefreshTokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, InitialAccessSession> activeInitialAccessTokens = new ConcurrentHashMap<>();

    public IssuedTokens issue(UUID userId, TokenAudience audience) {
        removeExpiredSessions();
        UUID tokenId = UUID.randomUUID();
        String accessToken = jwtTokenProvider.generateAccessToken(userId, audience, tokenId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId, audience, tokenId);
        activeAccessTokens.put(tokenId, new AccessSession(
                userId,
                audience,
                Instant.now().plusSeconds(jwtTokenProvider.getAccessTokenExpiration())));
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

    public IssuedInitialAccessToken issueInitialAccess(UUID userId, TokenAudience audience) {
        removeExpiredSessions();
        UUID tokenId = UUID.randomUUID();
        String accessToken = jwtTokenProvider.generateInitialAccessToken(userId, tokenId, audience);
        long expiresIn = jwtTokenProvider.getAccessTokenExpiration();
        activeInitialAccessTokens.put(tokenId,
                new InitialAccessSession(userId, audience, Instant.now().plusSeconds(expiresIn)));
        return new IssuedInitialAccessToken(accessToken, expiresIn);
    }

    /**
     * @param expectedAudience checked <em>before</em> the session is removed. Validating after
     *     removal would let a token presented to the wrong surface be destroyed on its way to
     *     being rejected.
     */
    public UUID consumeInitialAccess(String token, TokenAudience expectedAudience) {
        try {
            if (!jwtTokenProvider.validateToken(token)) {
                throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
            }
            Claims claims = jwtTokenProvider.getClaimsFromToken(token);
            if (!jwtTokenProvider.hasPurpose(claims, expectedAudience, TokenUse.INITIAL_ACCESS)
                    || claims.getId() == null) {
                throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
            }
            UUID tokenId = UUID.fromString(claims.getId());
            UUID userId = UUID.fromString(claims.getSubject());
            InitialAccessSession session = activeInitialAccessTokens.get(tokenId);
            if (session == null
                    || !session.userId().equals(userId)
                    || session.audience() != expectedAudience
                    || !activeInitialAccessTokens.remove(tokenId, session)) {
                throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
            }
            return userId;
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }
    }

    public void revoke(String refreshToken, TokenAudience expectedAudience) {
        ParsedRefreshToken parsed = parse(refreshToken, expectedAudience);
        RefreshSession session = activeRefreshTokens.get(parsed.tokenId());
        if (session == null
                || !session.userId().equals(parsed.userId())
                || session.audience() != expectedAudience
                || !activeRefreshTokens.remove(parsed.tokenId(), session)) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }
        activeAccessTokens.remove(parsed.tokenId());
    }

    /** Every ordinary access token is a live server-side session, not only a signed bearer. */
    public boolean isAccessActive(Claims claims, TokenAudience expectedAudience) {
        try {
            if (!jwtTokenProvider.hasPurpose(claims, expectedAudience, TokenUse.ACCESS)
                    || claims.getId() == null) {
                return false;
            }
            UUID tokenId = UUID.fromString(claims.getId());
            UUID userId = UUID.fromString(claims.getSubject());
            AccessSession session = activeAccessTokens.get(tokenId);
            return session != null
                    && session.userId().equals(userId)
                    && session.audience() == expectedAudience
                    && session.expiresAt().isAfter(Instant.now());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public void revokeAll(Collection<UUID> userIds, TokenAudience audience) {
        Set<UUID> revokedUserIds = Set.copyOf(userIds);
        activeAccessTokens.entrySet().removeIf(entry ->
                entry.getValue().audience() == audience
                        && revokedUserIds.contains(entry.getValue().userId()));
        activeRefreshTokens.entrySet().removeIf(entry ->
                entry.getValue().audience() == audience
                        && revokedUserIds.contains(entry.getValue().userId()));
        // Scoped to the audience being revoked: signing out of the client portal must not
        // destroy an operator's pending admin password change, or the reverse.
        activeInitialAccessTokens.entrySet().removeIf(entry ->
                entry.getValue().audience() == audience
                        && revokedUserIds.contains(entry.getValue().userId()));
    }

    private void removeExpiredSessions() {
        Instant now = Instant.now();
        activeAccessTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        activeRefreshTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        activeInitialAccessTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
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

    private record AccessSession(UUID userId, TokenAudience audience, Instant expiresAt) {
    }

    private record InitialAccessSession(UUID userId, TokenAudience audience, Instant expiresAt) {
    }

    private record ParsedRefreshToken(UUID userId, UUID tokenId) {
    }
}
