package com.hiveapp.platform.client.member.dto;

import com.hiveapp.identity.service.CredentialAccessMaterial;

/**
 * Outcome of member creation. The member is already projected to a read model, so no persistence
 * entity crosses the service boundary. Credential-email delivery status is deliberately not part
 * of this result: it is only known after the transaction commits, so it stays a post-commit
 * composition step for the caller.
 */
public record MemberCreationResult(MemberDto member, CredentialAccessMaterial initialAccess) {
}
