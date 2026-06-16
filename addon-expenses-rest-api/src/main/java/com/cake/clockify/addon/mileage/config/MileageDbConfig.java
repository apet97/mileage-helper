package com.cake.clockify.addon.mileage.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnExpression("!'${spring.autoconfigure.exclude:}'.contains('DataSourceAutoConfiguration')")
@EnableJpaRepositories(basePackages = {
        "com.cake.clockify.addon.mileage.settings",
        "com.cake.clockify.addon.mileage.audit",
        "com.cake.clockify.addon.mileage.policy"
})
@EntityScan(basePackages = {
        "com.cake.clockify.addon.db.entity",
        "com.cake.clockify.addon.mileage.settings",
        "com.cake.clockify.addon.mileage.audit",
        "com.cake.clockify.addon.mileage.policy"
})
public class MileageDbConfig {
}
