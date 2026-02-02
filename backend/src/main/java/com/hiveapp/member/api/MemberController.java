package com.hiveapp.member.api;

import com.hiveapp.member.domain.dto.AssignRoleRequest;
import com.hiveapp.member.domain.dto.CreateMemberRequest;
import com.hiveapp.member.domain.dto.MemberResponse;
import com.hiveapp.member.domain.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MemberResponse> createMember(@Valid @RequestBody CreateMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memberService.createMember(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MemberResponse> getMemberById(@PathVariable UUID id) {
        return ResponseEntity.ok(memberService.getMemberById(id));
    }

    @GetMapping("/account/{accountId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MemberResponse>> getMembersByAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(memberService.getMembersByAccountId(accountId));
    }

    @PostMapping("/{memberId}/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> assignRole(
            @PathVariable UUID memberId,
            @Valid @RequestBody AssignRoleRequest request
    ) {
        memberService.assignRole(memberId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{memberId}/roles/{roleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeRole(
            @PathVariable UUID memberId,
            @PathVariable UUID roleId,
            @RequestParam(required = false) UUID companyId
    ) {
        memberService.removeRole(memberId, roleId, companyId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deactivateMember(@PathVariable UUID id) {
        memberService.deactivateMember(id);
        return ResponseEntity.noContent().build();
    }
}
