package com.hiveapp.platform.client.account.api;

import org.springframework.web.bind.annotation.*;
import com.hiveapp.platform.client.account.dto.AccountDto;
import com.hiveapp.platform.client.account.mapper.AccountMapper;
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
    private final AccountMapper accountMapper;

    @GetMapping("/me")
    public ResponseEntity<AccountDto> getMyAccount() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        var account = accountService.getAccount(accountId);
        return ResponseEntity.ok(accountMapper.toDto(account));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deactivateMyAccount() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        accountService.deactivateAccount(accountId);
        return ResponseEntity.noContent().build();
    }
}
