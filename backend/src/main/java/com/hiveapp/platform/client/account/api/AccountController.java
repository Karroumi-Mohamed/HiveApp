package com.hiveapp.platform.client.account.api;

import org.springframework.web.bind.annotation.*;
import com.hiveapp.platform.generated.PlatformPermissions;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.service.AccountShellService;
import com.hiveapp.shared.security.HiveAppUserDetails;
import dev.karroumi.permissionizer.PermissionGuard;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@PermissionNode(key = "workspace", description = "Account & Workspace Management")
public class AccountController {
    private final AccountShellService accountService;

    @GetMapping("/me")
    @PermissionNode(key = "read", description = "Read my account")
    public ResponseEntity<Account> getMyAccount(
            @AuthenticationPrincipal HiveAppUserDetails userDetails) {
        PermissionGuard.check(PlatformPermissions.Client.Account.Workspace.Read.permission());
        return ResponseEntity.ok(accountService.getAllAccounts().stream().findFirst().orElse(null));
    }

    @GetMapping
    @PermissionNode(key = "view_all", description = "List all accounts (Support)")
    public ResponseEntity<List<Account>> getAll() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @PostMapping
    @PermissionNode(key = "provision", description = "Create new account")
    public ResponseEntity<Account> create(
            @RequestParam UUID ownerId,
            @RequestParam String name,
            @RequestParam String slug) {
        return ResponseEntity.ok(accountService.createAccount(ownerId, name, slug));
    }
}
