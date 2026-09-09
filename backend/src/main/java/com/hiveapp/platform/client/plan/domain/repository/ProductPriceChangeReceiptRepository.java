package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.ProductPriceChangeReceipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductPriceChangeReceiptRepository
        extends JpaRepository<ProductPriceChangeReceipt, UUID> {}
