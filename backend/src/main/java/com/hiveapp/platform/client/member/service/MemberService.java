package com.hiveapp.platform.client.member.service;

import com.hiveapp.platform.client.member.domain.entity.Member;
import java.util.List;
import java.util.UUID;

public interface MemberService {
    Member getMember(UUID id);
    Member addMember(UUID accountId, UUID userId, String displayName);
    void assignRole(UUID memberId, UUID roleId, UUID companyId);
}
