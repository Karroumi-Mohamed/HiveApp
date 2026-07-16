package com.hiveapp.identity.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    boolean passwordChangeRequired
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, false);
    }

    public static AuthResponse initialAccess(String accessToken, long expiresIn) {
        return new AuthResponse(accessToken, null, "Bearer", expiresIn, true);
    }
}
