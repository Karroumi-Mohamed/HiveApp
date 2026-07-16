package com.hiveapp.shared.security;

public record IssuedInitialAccessToken(String accessToken, long expiresIn) {
}
