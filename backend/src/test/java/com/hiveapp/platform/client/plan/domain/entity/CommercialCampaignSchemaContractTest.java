package com.hiveapp.platform.client.plan.domain.entity;

import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CommercialCampaignSchemaContractTest {

    @Test
    void lineageDraftSchedulingAndProvenanceQueriesHaveExplicitDatabaseContracts() {
        Table table = CommercialCampaign.class.getAnnotation(Table.class);
        Map<String, String> indexes = Arrays.stream(table.indexes())
                .collect(Collectors.toMap(index -> index.name(), index -> index.columnList()));
        Set<String> constraints = Arrays.stream(table.uniqueConstraints())
                .map(constraint -> constraint.name()).collect(Collectors.toSet());

        assertThat(constraints).contains(
                "uk_commercial_campaign_code",
                "uk_commercial_campaign_lineage_revision",
                "uk_commercial_campaign_draft_lineage");
        assertThat(indexes).containsEntry("idx_campaign_normalized_name", "normalized_name")
                .containsEntry("idx_campaign_status_start", "status,starts_at,id")
                .containsEntry("idx_campaign_status_end", "status,ends_at,id")
                .containsEntry("idx_campaign_source", "source_campaign_id")
                .containsEntry("idx_campaign_segment_status", "segment_id,status,id")
                .containsEntry("idx_campaign_segment_activation", "segment_activation_id");
    }
}
