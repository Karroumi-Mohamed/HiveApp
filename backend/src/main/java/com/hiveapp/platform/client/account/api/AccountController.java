package com.hiveapp.platform.client.account.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hiveapp.platform.generated.PlatformPermissions;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.service.AccountShellService;
import com.hiveapp.shared.security.HiveAppUserDetails;

import dev.karroumi.permissionizer.PermissionGuard;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountShellService accountService;
    
    @GetMapping("/me")
    @PermissionNode(key = "read", description = "Read my account")
    public ResponseEntity<Account> getMyAccount(
        @AuthenticationPrincipal HiveAppUserDetails userDetails
    ) {
        PermissionGuard.check(PlatformPermissions.Client.Account.Read.permission());
        // For testing purposes, we return the first account
        return ResponseEntity.ok(accountService.getAllAccounts().stream().findFirst().orElse(null));
    }
    
    @GetMapping
    public ResponseEntity<List<Account>> getAll() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }
}
