package com.hiveapp.platform.client.member.api;

import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.service.MemberService;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@PermissionNode(key = "staff", description = "Member Management")
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    @PermissionNode(key = "add", description = "Add member to account")
    public ResponseEntity<Member> addMember(
        @RequestParam UUID accountId,
        @RequestParam UUID userId,
        @RequestParam String displayName
    ) {
        return ResponseEntity.ok(memberService.addMember(accountId, userId, displayName));
    }

    @PostMapping("/{id}/roles")
    @PermissionNode(key = "assign_role", description = "Assign role to member")
    public ResponseEntity<Void> assignRole(
        @PathVariable UUID id,
        @RequestParam UUID roleId,
        @RequestParam(required = false) UUID companyId
    ) {
        memberService.assignRole(id, roleId, companyId);
        return ResponseEntity.noContent().build();
    }
}
