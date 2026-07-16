package com.hiveapp.platform.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.hiveapp.shared.email.EmailService;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prod-profile-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "hiveapp.jwt.secret=production-profile-test-secret-never-deploy",
        "hiveapp.activation.base-url=https://example.test",
        "spring.mail.host=localhost",
        "hiveapp.admin.bootstrap.enabled=false"
})
@ActiveProfiles("prod")
class AdminBootstrapProductionProfileTest {

    @MockBean
    private EmailService emailService;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void productionDoesNotCreateAdminSeederUnlessExplicitlyEnabled() {
        assertThat(applicationContext.getBeansOfType(AdminSeeder.class)).isEmpty();
    }
}
