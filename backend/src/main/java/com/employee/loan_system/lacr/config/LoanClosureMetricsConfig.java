package com.employee.loan_system.lacr.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer custom metric beans for the LACR system.
 *
 * Interview story:
 * "We expose custom Micrometer metrics so that our SRE team can set up Prometheus alerts.
 *  For example, if reconciliation_exceptions_total rate exceeds a threshold, it fires a PagerDuty alert.
 *  We chose Counter for total counts and Timer for settlement calculation so we can measure p95/p99 latency."
 */
@Configuration
public class LoanClosureMetricsConfig {

    /**
     * Counts total loan closure requests received (both new and idempotent).
     */
    @Bean
    public Counter closureRequestsTotal(MeterRegistry registry) {
        return Counter.builder("lacr.closure_requests_total")
                .description("Total number of loan closure intake requests (including idempotent duplicates)")
                .tag("service", "closure-intake")
                .register(registry);
    }

    /**
     * Counts idempotent duplicate requests (same requestId seen again).
     */
    @Bean
    public Counter idempotentDuplicatesTotal(MeterRegistry registry) {
        return Counter.builder("lacr.idempotent_duplicates_total")
                .description("Count of closure requests rejected as duplicates via idempotency check")
                .tag("service", "closure-intake")
                .register(registry);
    }

    /**
     * Counts reconciliation discrepancies (bank-confirmed != computed settlement).
     */
    @Bean
    public Counter reconciliationExceptionsTotal(MeterRegistry registry) {
        return Counter.builder("lacr.reconciliation_exceptions_total")
                .description("Total reconciliation cases where bank amount differed from calculated settlement")
                .tag("service", "reconciliation-engine")
                .register(registry);
    }

    /**
     * Timer for the full settlement calculation duration.
     * Enables p50/p95/p99 latency tracking in Prometheus/Grafana.
     */
    @Bean
    public Timer settlementCalculationTimer(MeterRegistry registry) {
        return Timer.builder("lacr.settlement_duration_seconds")
                .description("Time taken to calculate the final settlement amount for a closure case")
                .tag("service", "reconciliation-engine")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    /**
     * Counts events that exhausted all retry attempts and were written to the failed_events DLT.
     */
    @Bean
    public Counter failedEventsTotal(MeterRegistry registry) {
        return Counter.builder("lacr.failed_events_total")
                .description("Count of closure events that exhausted all retries and were sent to the dead letter table")
                .tag("service", "retry-handler")
                .register(registry);
    }
}
