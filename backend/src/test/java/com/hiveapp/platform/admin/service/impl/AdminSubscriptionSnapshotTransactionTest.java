package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSubscriptionSnapshotTransactionTest {

    @Test
    void accountRowsAndTheirLatestSubscriptionsShareOneRepeatableReadSnapshot() throws Exception {
        assertRepeatableRead("searchAccounts");
        assertRepeatableRead("findAccountsByOwnerEmail");
    }

    private void assertRepeatableRead(String methodName) throws Exception {
        Transactional transaction = AdminSubscriptionServiceImpl.class.getMethod(
                        methodName,
                        String.class,
                        Boolean.class,
                        SubscriptionStatus.class,
                        Boolean.class,
                        String.class,
                        Pageable.class)
                .getAnnotation(Transactional.class);

        assertThat(transaction).as(methodName + " transaction boundary").isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
