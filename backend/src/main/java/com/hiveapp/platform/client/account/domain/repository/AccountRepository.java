package com.hiveapp.platform.client.account.domain.repository;

import com.hiveapp.platform.client.account.domain.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    boolean existsByOwnerId(UUID ownerId);
    Optional<Account> findByOwnerId(UUID ownerId);
    boolean existsBySlug(String slug);
}
