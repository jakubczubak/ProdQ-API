package com.example.infraboxapi.supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for recalculating supplier performance KPIs.
 * Runs nightly at 2:00 AM to recalculate all supplier metrics.
 */
@Component
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class SupplierPerformanceScheduler {

    private final SupplierPerformanceService supplierPerformanceService;

    /**
     * Recalculate all supplier performance metrics daily at 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void recalculateAllSupplierPerformance() {
        log.info("Starting scheduled supplier performance recalculation...");
        try {
            supplierPerformanceService.recalculateAllSuppliers();
            log.info("Scheduled supplier performance recalculation completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled supplier performance recalculation: {}", e.getMessage(), e);
        }
    }
}
