package com.hiveapp.platform.client.account.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hiveapp.platform.client.account.domain.entity.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByOwnerId(UUID ownerId);
    boolean existsByOwnerId(UUID ownerId);
}
