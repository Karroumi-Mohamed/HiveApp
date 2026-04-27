package com.hiveapp.platform.client.account.api;

import com.hiveapp.platform.client.member.service.MemberService;
import com.hiveapp.platform.client.member.dto.MemberPermissionDto;
import com.hiveapp.shared.security.HiveAppUserDetails;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final MemberService memberService;

    @GetMapping("/permissions")
    public ResponseEntity<MemberPermissionDto> getPermissions(@AuthenticationPrincipal HiveAppUserDetails userDetails) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return ResponseEntity.ok(memberService.getEffectivePermissions(userDetails.getUserId(), accountId));
    }
}
