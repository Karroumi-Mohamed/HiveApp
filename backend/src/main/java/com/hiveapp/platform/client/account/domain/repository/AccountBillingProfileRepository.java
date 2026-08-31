package com.hiveapp.platform.client.account.domain.repository;

import com.hiveapp.platform.client.account.domain.entity.AccountBillingProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountBillingProfileRepository extends JpaRepository<AccountBillingProfile, UUID> {
    @EntityGraph(attributePaths = "account")
    Optional<AccountBillingProfile> findByAccountId(UUID accountId);
}
