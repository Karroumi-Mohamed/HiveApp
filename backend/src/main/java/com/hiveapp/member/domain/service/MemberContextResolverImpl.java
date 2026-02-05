package com.hiveapp.member.domain.service;

import com.hiveapp.member.domain.entity.Member;
import com.hiveapp.member.domain.repository.MemberRepository;
import com.hiveapp.shared.security.MemberContext;
import com.hiveapp.shared.security.MemberContextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of the MemberContextResolver for the JWT authentication filter.
 *
 * This bridges the shared security layer to the member module's data,
 * allowing the filter to set memberId/accountId on the security principal
 * without the shared module directly depending on member entities.
 */
@Service
@RequiredArgsConstructor
public class MemberContextResolverImpl implements MemberContextResolver {

    private final MemberRepository memberRepository;

    @Override
    public Optional<MemberContext> resolve(UUID userId, UUID accountId) {
        return memberRepository.findByUserIdAndAccountId(userId, accountId)
                .map(member -> new MemberContext(member.getId(), member.getAccountId()));
    }

    @Override
    public Optional<MemberContext> resolveDefault(UUID userId) {
        List<Member> members = memberRepository.findByUserId(userId);
        if (members.isEmpty()) {
            return Optional.empty();
        }
        Member defaultMember = members.getFirst();
        return Optional.of(new MemberContext(defaultMember.getId(), defaultMember.getAccountId()));
    }
}
