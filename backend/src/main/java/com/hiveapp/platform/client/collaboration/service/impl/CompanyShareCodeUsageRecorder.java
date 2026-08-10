package com.hiveapp.platform.client.collaboration.service.impl;

import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CompanyShareCodeUsageRecorder {

    private final CompanyRepository companyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordResolution(UUID companyId, String expectedHash, Instant occurredAt) {
        requireCurrentCode(companyRepository.recordB2bShareResolution(companyId, expectedHash, occurredAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordRequest(UUID companyId, String expectedHash, Instant occurredAt) {
        requireCurrentCode(companyRepository.recordB2bShareRequest(companyId, expectedHash, occurredAt));
    }

    private void requireCurrentCode(int updatedRows) {
        if (updatedRows != 1) {
            throw new ResourceNotFoundException("CompanyShareCode", "code", "invalid");
        }
    }
}
