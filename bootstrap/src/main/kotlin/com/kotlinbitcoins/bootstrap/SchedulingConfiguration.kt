package com.kotlinbitcoins.bootstrap

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * ShedLock for the scheduled jobs: with several replicas, only one runs each tick of the quote capture and purge
 * (FR-027, FR-029). The lock lives in the `shedlock` table and uses the database clock, so replicas with skewed
 * clocks still agree on who holds it.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class SchedulingConfiguration {
    @Bean
    fun lockProvider(dataSource: DataSource): LockProvider =
        JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration
                .builder()
                .withJdbcTemplate(JdbcTemplate(dataSource))
                .usingDbTime()
                .build(),
        )
}
