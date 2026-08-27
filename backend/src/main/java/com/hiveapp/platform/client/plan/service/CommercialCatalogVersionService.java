package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.CommercialCatalogRevision;
import com.hiveapp.platform.client.plan.domain.repository.CommercialCatalogRevisionRepository;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.LongFunction;

@Service
@RequiredArgsConstructor
public class CommercialCatalogVersionService {

    public static final String LOCK_NAME = "commercial-catalog";

    private final CommercialCatalogRevisionRepository repository;

    /** Scalar query deliberately bypasses the persistence-context entity cache. */
    public long currentRevision() {
        return repository.findRevision(LOCK_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Commercial catalogue revision authority is unavailable"));
    }

    public CommercialCatalogRevision lockForMutation() {
        return repository.findByLockNameForUpdate(LOCK_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Commercial catalogue mutation lock is unavailable"));
    }

    public void bump(UUID revisionId) {
        if (repository.incrementRevision(revisionId) != 1) {
            throw new IllegalStateException("Commercial catalogue revision could not be advanced");
        }
    }

    /**
     * Detects a committed catalogue mutation interleaved with a multi-query preview. A mutation
     * committing just after the final read is still safe: its newer revision invalidates apply.
     */
    public <T> T readConsistently(LongFunction<T> reader) {
        long before = currentRevision();
        T result = reader.apply(before);
        long after = currentRevision();
        if (before != after) {
            throw stalePreview();
        }
        return result;
    }

    public void requireCurrent(long suppliedRevision) {
        if (currentRevision() != suppliedRevision) {
            throw stalePreview();
        }
    }

    public StaleResourceVersionException stalePreview() {
        return new StaleResourceVersionException(
                "Commercial catalogue changed during review. Reload the preview and retry.");
    }
}
