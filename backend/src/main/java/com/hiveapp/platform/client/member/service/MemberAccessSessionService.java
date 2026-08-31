package com.hiveapp.platform.client.member.service;

import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.shared.security.TokenAudience;
import com.hiveapp.shared.security.TokenSessionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Member-domain boundary for revoking every current client session in an Account. */
@Service
@RequiredArgsConstructor
public class MemberAccessSessionService {
    private final MemberRepository members;
    private final TokenSessionService sessions;

    @Transactional(readOnly = true)
    public void revokeAllForAccount(UUID accountId) {
        sessions.revokeAll(
                members.findAllByAccountId(accountId).stream()
                        .map(member -> member.getUser().getId())
                        .toList(),
                TokenAudience.CLIENT);
    }
}
