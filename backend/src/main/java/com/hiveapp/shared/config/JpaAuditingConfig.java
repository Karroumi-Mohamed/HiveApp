package com.hiveapp.shared.config;

import org.springframework.core.Ordered;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaAuditing
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class JpaAuditingConfig {

    
}
