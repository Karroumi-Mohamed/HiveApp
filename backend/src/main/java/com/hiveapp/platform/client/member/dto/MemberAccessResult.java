package com.hiveapp.platform.client.member.dto;

import com.hiveapp.identity.service.CredentialAccessMaterial;

import java.util.UUID;

public record MemberAccessResult(UUID memberId, CredentialAccessMaterial access) {
}
