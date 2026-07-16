package com.hiveapp.platform.client.member.dto;

import com.hiveapp.identity.service.CredentialAccessMaterial;
import com.hiveapp.platform.client.member.domain.entity.Member;

public record MemberCreationResult(Member member, CredentialAccessMaterial initialAccess) {
}
