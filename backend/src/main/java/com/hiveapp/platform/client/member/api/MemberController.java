package com.hiveapp.platform.client.member.api;

import com.hiveapp.platform.client.member.dto.AddMemberRequest;
import com.hiveapp.platform.client.member.dto.AssignRoleRequest;
import com.hiveapp.platform.client.member.dto.MemberDto;
import com.hiveapp.platform.client.member.dto.OverridePermissionRequest;
import com.hiveapp.platform.client.member.mapper.MemberMapper;
import com.hiveapp.platform.client.member.service.MemberService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.quota.QuotaEnforcer;
import com.hiveapp.platform.client.feature.PlatformFeature;

import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@PermissionNode(key = "staff", description = "Member Management")
public class MemberController {

    private final MemberService memberService;
    private final MemberMapper memberMapper;
    private final QuotaEnforcer quotaEnforcer;

    @GetMapping
    @PermissionNode(key = "read", description = "List members in current account")
    public List<MemberDto> getMembers() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return memberService.getAccountMembers(accountId).stream()
                .map(memberMapper::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PermissionNode(key = "add", description = "Add member to account")
    public MemberDto addMember(@Valid @RequestBody AddMemberRequest req) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        
        quotaEnforcer.check(
            PlatformFeature.WORKSPACE,
            PlatformFeature.MEMBERS,
            accountId,
            () -> (long) memberService.getAccountMembers(accountId).size()
        );
        
        var member = memberService.addMember(accountId, req.userId(), req.displayName());
        return memberMapper.toDto(member);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermissionNode(key = "delete", description = "Deactivate member")
    public void deactivateMember(@PathVariable UUID id) {
        // Need to add validation to ensure member is part of current account
        memberService.deactivateMember(id);
    }

    @PostMapping("/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermissionNode(key = "assign_role", description = "Assign role to member")
    public void assignRole(
        @PathVariable UUID id,
        @Valid @RequestBody AssignRoleRequest req
    ) {
        memberService.assignRole(id, req.roleId(), req.companyId());
    }

    @PostMapping("/{id}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermissionNode(key = "grant_permission", description = "Grant/Deny direct permission")
    public void grantPermissionOverride(
        @PathVariable UUID id,
        @Valid @RequestBody OverridePermissionRequest req
    ) {
        memberService.grantPermissionOverride(id, req.permissionCode(), req.companyId(), req.decision());
    }

    @DeleteMapping("/{id}/permissions/{permissionCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermissionNode(key = "revoke_permission", description = "Remove direct permission")
    public void revokePermissionOverride(
        @PathVariable UUID id,
        @PathVariable String permissionCode,
        @RequestParam UUID companyId
    ) {
        memberService.revokePermissionOverride(id, permissionCode, companyId);
    }
}
