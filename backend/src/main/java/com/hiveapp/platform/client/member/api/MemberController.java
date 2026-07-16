package com.hiveapp.platform.client.member.api;

import com.hiveapp.platform.client.member.dto.CreateMemberRequest;
import com.hiveapp.platform.client.member.dto.MemberAccessResponse;
import com.hiveapp.platform.client.member.dto.MemberCreationResponse;
import com.hiveapp.platform.client.member.dto.AssignRoleRequest;
import com.hiveapp.platform.client.member.dto.MemberDto;
import com.hiveapp.platform.client.member.dto.MemberPermissionOverrideDto;
import com.hiveapp.platform.client.member.dto.OverridePermissionRequest;
import com.hiveapp.platform.client.member.dto.UpdateMemberRequest;
import com.hiveapp.platform.client.member.mapper.MemberMapper;
import com.hiveapp.platform.client.member.service.MemberService;
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

    @GetMapping
    public List<MemberDto> getMembers() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return memberService.getAccountMembers(accountId).stream()
                .map(memberMapper::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberCreationResponse createMember(@Valid @RequestBody CreateMemberRequest req) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        var result = memberService.createMember(accountId, req);
        var access = result.initialAccess();
        return new MemberCreationResponse(
                memberMapper.toDto(result.member()),
                access.method(),
                access.state(),
                access.temporaryPassword(),
                access.linkExpiresAt());
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

    @PostMapping("/{id}/access/regenerate")
    public MemberAccessResponse regenerateInitialAccess(@PathVariable UUID id) {
        return memberService.regenerateInitialAccess(id);
    }

    @PostMapping("/{id}/access/reset")
    public MemberAccessResponse resetAccess(@PathVariable UUID id) {
        return memberService.resetAccess(id);
    }

    @PostMapping("/{id}/access/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlockInitialAccess(@PathVariable UUID id) {
        memberService.unlockInitialAccess(id);
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
    public List<MemberPermissionOverrideDto> getMemberOverrides(@PathVariable UUID id, @RequestParam UUID companyId) {
        return memberService.getMemberOverrides(id, companyId);
    }
}
