package com.hiveapp.identity.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn
) {
    public static AuthResponse of(String accessToken, String refreshToken, String tokenType, long expiresIn) {
        return new AuthResponse(accessToken, refreshToken, tokenType, expiresIn);
    }
}
