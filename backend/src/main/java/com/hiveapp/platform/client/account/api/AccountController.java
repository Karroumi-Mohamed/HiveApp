package com.hiveapp.platform.client.account.api;

import org.springframework.web.bind.annotation.*;
import com.hiveapp.platform.client.account.dto.AccountDto;
import com.hiveapp.platform.client.account.service.AccountShellService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountShellService accountService;

    @GetMapping("/me")
    public ResponseEntity<AccountDto> getMyAccount() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return ResponseEntity.ok(accountService.getAccount(accountId));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deactivateMyAccount() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        accountService.deactivateAccount(accountId);
        return ResponseEntity.noContent().build();
    }
}
