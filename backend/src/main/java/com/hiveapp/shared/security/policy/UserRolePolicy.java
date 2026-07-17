package com.hiveapp.shared.security.policy;

import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideDecision;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class UserRolePolicy implements PermissionPolicy {

    private final MemberRepository memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final MemberPermissionOverrideRepository overrideRepository;

    @Override
    public Decision evaluate(Permission requested, Object context) {
        if (!(context instanceof HiveAppPermissionContext ctx) || ctx.actorUserId() == null) {
            return Decision.ABSTAIN;
        }

        var member = memberRepository.findByAccountIdAndUserId(ctx.currentAccountId(), ctx.actorUserId())
            .orElse(null);
        if (member == null) return Decision.DENIED;

        // Workspace owner has full access to everything in their workspace.
        // Overrides and role assignments are irrelevant — owner can always act.
        if (member.isOwner()) return Decision.GRANTED;

        // Applicable active DENY exceptions always win over GRANT exceptions and roles.
        var overrides = overrideRepository.findApplicable(member.getId(), ctx.targetCompanyId());
        Instant now = Instant.now();
        boolean grant = false;
        for (var exception : overrides) {
            if (!exception.isEffectiveAt(now)
                    || !exception.getPermission().getCode().equals(requested.path())) {
                continue;
            }
            if (exception.getScopeCompany() != null && !exception.getScopeCompany().isActive()) {
                continue;
            }
            if (exception.getDecision() == PermissionOverrideDecision.DENY) {
                return Decision.DENIED;
            }
            grant = true;
        }
        if (grant) return Decision.GRANTED;

        // 2. Check Roles (Union of all assigned roles for this company scope)
        boolean hasRoleGrant = memberRoleRepository.existsByMemberIdAndPermissionCode(member.getId(), requested.path(), ctx.targetCompanyId());

        return hasRoleGrant ? Decision.GRANTED : Decision.ABSTAIN;
    }
}
