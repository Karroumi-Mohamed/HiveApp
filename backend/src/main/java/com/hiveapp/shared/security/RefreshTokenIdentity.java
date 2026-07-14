package com.hiveapp.shared.security;

import java.util.UUID;

public record RefreshTokenIdentity(
        UUID userId,
        TokenAudience audience
) {
}
