package com.hiveapp.shared.security.policy;

import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionGuard;
import dev.karroumi.permissionizer.PermissionPolicy;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserRolePolicy implements PermissionPolicy {

    private final MemberRepository memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final MemberPermissionOverrideRepository overrideRepository;

    @Override
    public Decision evaluate(Permission requested, Object context) {
        if (!(context instanceof HiveAppPermissionContext ctx)) {
            return Decision.ABSTAIN;
        }

        var member = memberRepository.findByAccountIdAndUserId(ctx.getCurrentAccountId(), ctx.getActorUserId())
            .orElse(null);
        if (member == null) return Decision.DENIED;

        // 1. Check Overrides (High Priority)
        var overrides = overrideRepository.findAllByMemberIdAndCompanyId(member.getId(), ctx.getTargetCompanyId());
        for (var o : overrides) {
            if (o.getPermission().getCode().equals(requested.path())) {
                return o.isDecision() ? Decision.GRANTED : Decision.DENIED;
            }
        }

        // 2. Check Roles
        var roles = memberRoleRepository.findAllByMemberId(member.getId());
        for (var mr : roles) {
            // Check if role is scoped to this company or account-wide (company_id is null)
            if (mr.getCompany() == null || mr.getCompany().getId().equals(ctx.getTargetCompanyId())) {
                boolean hasPerm = mr.getRole().isActive() && true; // Join check logic here
                if (hasPerm) return Decision.GRANTED;
            }
        }

        return Decision.ABSTAIN;
    }
}
