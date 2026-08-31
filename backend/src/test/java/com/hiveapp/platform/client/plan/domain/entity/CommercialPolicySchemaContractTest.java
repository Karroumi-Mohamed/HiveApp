package com.hiveapp.platform.client.plan.domain.entity;

import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommercialPolicySchemaContractTest {

    @Test
    void segmentReferenceLookupHasAnExplicitDatabaseIndex() {
        Table table = CommercialPolicy.class.getAnnotation(Table.class);

        assertThat(List.of(table.indexes())).anySatisfy(index -> {
            assertThat(index.name()).isEqualTo("idx_commercial_policy_segment_reference");
            assertThat(index.columnList()).isEqualTo("segment_reference");
        });
    }
}
