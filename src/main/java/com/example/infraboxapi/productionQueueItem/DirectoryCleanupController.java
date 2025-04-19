package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.notification.NotificationDescription;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/cleanup")
public class DirectoryCleanupController {

    private final DirectoryCleanupService directoryCleanupService;
    private final MachineRepository machineRepository;
    private final NotificationService notificationService;

    public DirectoryCleanupController(
            DirectoryCleanupService directoryCleanupService,
            MachineRepository machineRepository,
            NotificationService notificationService) {
        this.directoryCleanupService = directoryCleanupService;
        this.machineRepository = machineRepository;
        this.notificationService = notificationService;
    }

    @PostMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> triggerFullCleanup() {
        try {
            directoryCleanupService.cleanupAllMachines();
            return ResponseEntity.ok("Manual directory cleanup for all machines has been triggered.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error during manual cleanup: " + e.getMessage());
        }
    }

    @PostMapping("/machine/{queueType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> triggerCleanupForMachine(@PathVariable String queueType) {
        try {
            Integer machineId = Integer.parseInt(queueType);
            var machineOpt = machineRepository.findById(machineId);
            if (machineOpt.isEmpty()) {
                return ResponseEntity.status(404).body("Machine with queueType " + queueType + " does not exist.");
            }
            Machine machine = machineOpt.get();
            DirectoryCleanupService.CleanupResult result = directoryCleanupService.cleanUnusedDirectories(machine.getProgramPath(), queueType);

            // Send system notification
            String description = String.format(
                    "Manual directory cleanup for machine %s completed. Deleted %d directories. %d directories were blocked and require further attention.",
                    machine.getMachineName(), result.deletedDirectories, result.blockedDirectories
            );
            notificationService.createAndSendSystemNotification(description, NotificationDescription.DirectoryCleanupCompleted);

            return ResponseEntity.ok(
                    String.format(
                            "Manual directory cleanup for machine %s (queueType: %s) triggered successfully. Deleted %d directories, %d blocked.",
                            machine.getMachineName(), queueType, result.deletedDirectories, result.blockedDirectories
                    )
            );
        } catch (NumberFormatException e) {
            return ResponseEntity.status(400).body("Invalid queueType format: " + queueType);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error during manual cleanup: " + e.getMessage());
        }
    }
}