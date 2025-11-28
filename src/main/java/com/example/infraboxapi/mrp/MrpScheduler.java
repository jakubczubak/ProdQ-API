package com.example.infraboxapi.mrp;

import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler for automatic MRP analysis.
 * Runs daily at 6:00 AM Warsaw time.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MrpScheduler {

    private final MrpService mrpService;
    private final NotificationService notificationService;

    /**
     * Daily MRP analysis at 6:00 AM (Warsaw timezone)
     * Cron: second minute hour day-of-month month day-of-week
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "Europe/Warsaw")
    public void dailyMrpAnalysis() {
        log.info("=== Starting scheduled daily MRP analysis ===");

        try {
            List<MrpOrderSuggestionGroup> suggestions = mrpService.runFullAnalysisWithGrouping();

            // Count by priority
            long criticalCount = suggestions.stream()
                    .filter(g -> g.getHighestPriority() == MrpPriority.CRITICAL)
                    .count();
            long highCount = suggestions.stream()
                    .filter(g -> g.getHighestPriority() == MrpPriority.HIGH)
                    .count();

            log.info("Daily MRP analysis completed. Found {} suggestion groups ({} critical, {} high priority)",
                    suggestions.size(), criticalCount, highCount);

            // Send system notification if there are critical or high priority items
            if (criticalCount > 0) {
                String message = String.format(
                        "Analiza MRP wykryła %d krytycznych niedoborów wymagających natychmiastowej akcji!",
                        criticalCount
                );
                notificationService.createAndSendSystemNotification(message, NotificationDescription.MrpCriticalShortage);
            } else if (highCount > 0) {
                String message = String.format(
                        "Analiza MRP: %d pozycji wymaga pilnej uwagi (deadline < 3 dni)",
                        highCount
                );
                notificationService.createAndSendSystemNotification(message, NotificationDescription.MrpHighPriorityItems);
            }

        } catch (Exception e) {
            log.error("Error during scheduled MRP analysis", e);
        }
    }

    /**
     * Optional: Run analysis at noon for critical items check
     * Uncomment if needed
     */
    // @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Warsaw")
    // public void middayMrpCheck() {
    //     log.info("Running midday MRP critical check");
    //     // Similar logic but only for critical items
    // }
}
