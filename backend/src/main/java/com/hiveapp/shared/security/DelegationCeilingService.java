package com.hiveapp.shared.security;

import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DelegationCeilingService {

    private final EffectivePermissionService effectivePermissionService;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public void requireActorCanDelegate(UUID accountId, UUID companyId, Collection<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) return;
        var context = HiveAppContextHolder.getContext();
        if (context == null || context.actorUserId() == null) {
            throw new ForbiddenException("An authenticated member is required to delegate permissions");
        }
        var access = effectivePermissionService.getEffectivePermissions(
                context.actorUserId(), accountId, companyId);
        for (String code : permissionCodes) {
            if (!access.permissions().contains(code)) {
                throw new ForbiddenException(
                        "Cannot delegate a permission the acting member does not hold in the requested scope: " + code);
            }
        }
    }

    @Transactional(readOnly = true)
    public boolean isActorOwner(UUID accountId) {
        var context = HiveAppContextHolder.getContext();
        if (context == null || context.actorUserId() == null) return false;
        return memberRepository.findByAccountIdAndUserId(accountId, context.actorUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Member", "userId", context.actorUserId()))
                .isOwner();
    }
}
