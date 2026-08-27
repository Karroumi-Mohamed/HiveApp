package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.CommercialCatalogRevision;
import com.hiveapp.platform.client.plan.domain.repository.CommercialCatalogRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommercialCatalogRevisionInitializer {

    private final CommercialCatalogRevisionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists() {
        if (repository.findByLockName(CommercialCatalogVersionService.LOCK_NAME).isPresent()) {
            return;
        }
        CommercialCatalogRevision revision = new CommercialCatalogRevision();
        revision.setLockName(CommercialCatalogVersionService.LOCK_NAME);
        revision.setRevision(0);
        repository.saveAndFlush(revision);
    }
}
