package com.hiveapp.platform.client.account.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDirectoryServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @InjectMocks private AccountDirectoryServiceImpl service;

    @Test
    void searchReturnsOnlyTheMinimumAccountIdentityNeededByAChooser() {
        User owner = new User();
        owner.setEmail("owner@example.com");
        Account account = Account.builder()
                .owner(owner)
                .name("Northwind")
                .slug("northwind")
                .isActive(true)
                .build();
        UUID accountId = UUID.randomUUID();
        ReflectionTestUtils.setField(account, "id", accountId);
        PageRequest page = PageRequest.of(1, 20);
        when(accountRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(page)))
                .thenReturn(new PageImpl<>(List.of(account), page, 21));

        var result = service.search("  north  ", page);

        assertThat(result.getTotalElements()).isEqualTo(21);
        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(accountId);
            assertThat(item.name()).isEqualTo("Northwind");
            assertThat(item.slug()).isEqualTo("northwind");
            assertThat(item.active()).isTrue();
        });
    }
}
