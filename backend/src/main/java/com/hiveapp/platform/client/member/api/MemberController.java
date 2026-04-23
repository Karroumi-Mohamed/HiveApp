package com.hiveapp.platform.client.member.api;

import com.hiveapp.platform.client.feature.PlatformFeature;
import com.hiveapp.platform.client.member.dto.AddMemberRequest;
import com.hiveapp.platform.client.member.dto.AssignRoleRequest;
import com.hiveapp.platform.client.member.dto.MemberDto;
import com.hiveapp.platform.client.member.dto.OverridePermissionRequest;
import com.hiveapp.platform.client.member.dto.UpdateMemberRequest;
import com.hiveapp.platform.client.member.mapper.MemberMapper;
import com.hiveapp.platform.client.member.service.MemberService;
import com.hiveapp.shared.quota.QuotaEnforcer;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberMapper memberMapper;
    private final QuotaEnforcer quotaEnforcer;

    @GetMapping
    public List<MemberDto> getMembers() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return memberService.getAccountMembers(accountId).stream()
                .map(memberMapper::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberDto addMember(@Valid @RequestBody AddMemberRequest req) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();

        quotaEnforcer.check(
                PlatformFeature.WORKSPACE,
                PlatformFeature.MEMBERS,
                accountId,
                () -> (long) memberService.getAccountMembers(accountId).size()
        );

        return memberMapper.toDto(memberService.addMember(accountId, req.userId(), req.displayName()));
    }

    @PatchMapping("/{id}")
    public MemberDto updateMember(@PathVariable UUID id, @Valid @RequestBody UpdateMemberRequest req) {
        return memberMapper.toDto(memberService.updateMember(id, req.displayName()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateMember(@PathVariable UUID id) {
        memberService.deactivateMember(id);
    }

    // ── Role assignments ──────────────────────────────────────────────────────

    @PostMapping("/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignRole(@PathVariable UUID id, @Valid @RequestBody AssignRoleRequest req) {
        memberService.assignRole(id, req.roleId(), req.companyId());
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRole(@PathVariable UUID id, @PathVariable UUID roleId) {
        memberService.removeRole(id, roleId);
    }

    // ── Permission overrides ──────────────────────────────────────────────────

    @PostMapping("/{id}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantPermissionOverride(@PathVariable UUID id,
                                        @Valid @RequestBody OverridePermissionRequest req) {
        memberService.grantPermissionOverride(id, req.permissionCode(), req.companyId(), req.decision());
    }

    @DeleteMapping("/{id}/permissions/{permissionCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokePermissionOverride(@PathVariable UUID id,
                                         @PathVariable String permissionCode,
                                         @RequestParam UUID companyId) {
        memberService.revokePermissionOverride(id, permissionCode, companyId);
    }

    @GetMapping("/{id}/permissions")
    public List<?> getMemberOverrides(@PathVariable UUID id, @RequestParam UUID companyId) {
        return memberService.getMemberOverrides(id, companyId);
    }
}
