package com.hiveapp.shared.security;

public record IssuedTokens(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
