package com.hiveapp.platform.client.account.domain.repository;
import com.hiveapp.platform.client.account.domain.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AccountRepository extends JpaRepository<Account, UUID> {}
