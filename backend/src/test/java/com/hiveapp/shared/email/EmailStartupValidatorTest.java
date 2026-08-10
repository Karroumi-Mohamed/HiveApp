package com.hiveapp.shared.email;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailStartupValidatorTest {

    @Test
    void productionCannotStartWithoutSmtpHost() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new EmailStartupValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host SMTP configuration");
    }

    @Test
    void productionCannotStartWithBlankSmtpHost() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.mail.host", "   ");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new EmailStartupValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host SMTP configuration");
    }

    @Test
    void productionAcceptsConfiguredSmtpHost() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.mail.host", "smtp.example.com");
        environment.setActiveProfiles("prod");

        assertThatCode(() -> new EmailStartupValidator(environment).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void nonProductionMayUseTheSuppressedDeliveryFallback() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        assertThatCode(() -> new EmailStartupValidator(environment).validate())
                .doesNotThrowAnyException();
    }
}
