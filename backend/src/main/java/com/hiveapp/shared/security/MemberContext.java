package com.hiveapp.shared.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Lightweight DTO for passing member context during authentication.
 * Avoids coupling the shared module to the member module's entities.
 */
@Getter
@AllArgsConstructor
public class MemberContext {

    private final UUID memberId;
    private final UUID accountId;
}
