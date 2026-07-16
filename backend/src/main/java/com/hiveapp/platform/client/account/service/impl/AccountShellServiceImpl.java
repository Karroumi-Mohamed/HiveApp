package com.hiveapp.platform.client.account.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.dto.AccountDto;
import com.hiveapp.platform.client.account.mapper.AccountMapper;
import com.hiveapp.platform.client.account.service.AccountShellService;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.TokenAudience;
import com.hiveapp.shared.security.TokenSessionService;

import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = WorkspaceFeature.KEY, description = "Account & Workspace Management", guard = PermissionNode.Guard.ON)
public class AccountShellServiceImpl extends ClientWorkspaceFeatureService implements AccountShellService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final MemberRepository memberRepository;
    private final TokenSessionService tokenSessionService;

    @Override
    protected FeatureDefinition featureDefinition() {
        return WorkspaceFeature.definition();
    }

    @Override
    @PermissionNode(key = "read", description = "Read my account")
    @Transactional(readOnly = true)
    public AccountDto getAccount(UUID id) {
        return accountMapper.toDto(findCurrentAccount(id));
    }

    @Override
    @Transactional
    @PermissionNode(key = "delete", description = "Deactivate my account")
    public void deactivateAccount(UUID id) {
        var account = findCurrentAccount(id);
        var memberUserIds = memberRepository.findAllByAccountId(id).stream()
                .map(member -> member.getUser().getId())
                .toList();
        account.setActive(false);
        accountRepository.saveAndFlush(account);
        tokenSessionService.revokeAll(memberUserIds, TokenAudience.CLIENT);
    }

    private Account findCurrentAccount(UUID id) {
        var account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
        requireCurrentAccount(account);
        return account;
    }

    private void requireCurrentAccount(Account account) {
        UUID currentAccountId = HiveAppContextHolder.getContext().currentAccountId();
        if (!account.getId().equals(currentAccountId)) {
            throw new ForbiddenException("Account does not belong to the current workspace");
        }
    }
}
