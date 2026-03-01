package com.hiveapp.platform.client.account.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hiveapp.platform.client.account.dto.AccountDto;
import com.hiveapp.platform.client.account.service.AccountService;
import com.hiveapp.shared.security.HiveAppUserDetails;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;
    
    @GetMapping("/me")
    public ResponseEntity<AccountDto> getMyAccount(
        @AuthenticationPrincipal HiveAppUserDetails userDetails
    ) {
        return ResponseEntity.ok(accountService.getAccountByUserId(userDetails.getUserId()));
    }
    
}
