package com.hiveapp.account.api;

import com.hiveapp.account.domain.dto.AccountResponse;
import com.hiveapp.account.domain.dto.CreateAccountRequest;
import com.hiveapp.account.domain.dto.UpdateAccountRequest;
import com.hiveapp.account.domain.service.AccountService;
import com.hiveapp.shared.security.HiveAppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountResponse> createAccount(
            @AuthenticationPrincipal HiveAppUserDetails userDetails,
            @Valid @RequestBody CreateAccountRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(userDetails.getUserId(), request));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountResponse> getMyAccount(@AuthenticationPrincipal HiveAppUserDetails userDetails) {
        return ResponseEntity.ok(accountService.getAccountByOwnerId(userDetails.getUserId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @GetMapping("/slug/{slug}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountResponse> getAccountBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(accountService.getAccountBySlug(slug));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountRequest request
    ) {
        return ResponseEntity.ok(accountService.updateAccount(id, request));
    }

    @PatchMapping("/{id}/plan/{planId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePlan(@PathVariable UUID id, @PathVariable UUID planId) {
        accountService.changePlan(id, planId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> suspendAccount(@PathVariable UUID id) {
        accountService.suspendAccount(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activateAccount(@PathVariable UUID id) {
        accountService.activateAccount(id);
        return ResponseEntity.noContent().build();
    }
}
