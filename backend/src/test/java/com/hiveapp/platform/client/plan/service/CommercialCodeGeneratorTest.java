package com.hiveapp.platform.client.plan.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CommercialCodeGeneratorTest {

    @Test
    void generatesHiddenStableFormatWithoutRequiringAnOperatorCode() {
        String code = CommercialCodeGenerator.generate("Équipe croissance", "PLAN", ignored -> false);

        assertThat(code).matches("EQUIPE_CROISSANCE_[A-F0-9]{8}");
        assertThat(code).hasSizeLessThanOrEqualTo(100);
    }

    @Test
    void retriesWhenTheGeneratedIdentifierIsAlreadyUsed() {
        Set<String> seen = new HashSet<>();
        String first = CommercialCodeGenerator.generate("Flex", "PLAN", seen::contains);
        seen.add(first);
        String second = CommercialCodeGenerator.generate("Flex", "PLAN", seen::contains);

        assertThat(second).startsWith("FLEX_").isNotEqualTo(first);
    }

    @Test
    void fallsBackSafelyForNamesWithoutLatinLettersOrNumbers() {
        String code = CommercialCodeGenerator.generate("العربية", "QUOTA_PACKAGE", ignored -> false);

        assertThat(code).matches("QUOTA_PACKAGE_[A-F0-9]{8}");
    }
}
