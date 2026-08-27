package com.hiveapp.platform.client.account.service.impl;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.account.service.AccountDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.shared.exception.InvalidRequestException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountDirectoryServiceImpl implements AccountDirectoryService {

    private static final int MAX_SEARCH_LENGTH = 160;

    private final AccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AccountDirectoryEntryDto> search(String query, Pageable pageable) {
        return search(query, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountDirectoryEntryDto> search(String query, Boolean active, Pageable pageable) {
        String normalized = normalizeSearch(query);
        Specification<Account> specification = (root, ignored, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (normalized != null) {
                String pattern = "%" + escapeLike(normalized.toLowerCase(Locale.ROOT)) + "%";
                var matches = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                matches.add(cb.like(cb.lower(root.get("name")), pattern, '\\'));
                matches.add(cb.like(cb.lower(root.get("slug")), pattern, '\\'));
                parseUuid(normalized).ifPresent(id -> matches.add(cb.equal(root.get("id"), id)));
                predicates.add(cb.or(matches.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("isActive"), active));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return accountRepository.findAll(specification, pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDirectoryEntryDto> resolve(Collection<UUID> ids) {
        LinkedHashSet<UUID> bounded = validateChoiceIds(ids);
        return accountRepository.findAllByIdInOrderByNameAscIdAsc(bounded).stream()
                .map(this::toDto)
                .toList();
    }

    private AccountDirectoryEntryDto toDto(Account account) {
        return new AccountDirectoryEntryDto(
                account.getId(), account.getName(), account.getSlug(),
                account.isActive());
    }

    private String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_SEARCH_LENGTH) {
            throw new InvalidRequestException(
                    "Account search must not exceed " + MAX_SEARCH_LENGTH + " characters.");
        }
        return normalized;
    }

    private LinkedHashSet<UUID> validateChoiceIds(Collection<UUID> values) {
        if (values == null || values.isEmpty()) {
            throw new InvalidRequestException("At least one selected account id is required.");
        }
        if (values.size() > 100 || values.stream().anyMatch(java.util.Objects::isNull)) {
            throw new InvalidRequestException(
                    "Selected account ids must contain between 1 and 100 values.");
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>(values);
        if (ids.size() != values.size()) {
            throw new InvalidRequestException("Selected account ids must not contain duplicates.");
        }
        return ids;
    }

    private java.util.Optional<UUID> parseUuid(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
