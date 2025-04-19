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
            return ResponseEntity.ok("Ręczne czyszczenie katalogów dla wszystkich maszyn zostało uruchomione.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Błąd podczas ręcznego czyszczenia: " + e.getMessage());
        }
    }

    @PostMapping("/machine/{queueType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> triggerCleanupForMachine(@PathVariable String queueType) {
        try {
            Integer machineId = Integer.parseInt(queueType);
            var machineOpt = machineRepository.findById(machineId);
            if (machineOpt.isEmpty()) {
                return ResponseEntity.status(404).body("Maszyna o queueType " + queueType + " nie istnieje.");
            }
            Machine machine = machineOpt.get();
            DirectoryCleanupService.CleanupResult result = directoryCleanupService.cleanUnusedDirectories(machine.getProgramPath(), queueType);

            // Wysłanie powiadomienia systemowego
            String description = String.format(
                    "Ręczne czyszczenie katalogów dla maszyny %s (queueType: %s) zakończone. Usunięto %d katalogów. %d katalogów było zablokowanych i wymaga dalszej uwagi.",
                    machine.getMachineName(), queueType, result.deletedDirectories, result.blockedDirectories
            );
            notificationService.createAndSendSystemNotification(description, NotificationDescription.DirectoryCleanupCompleted);

            return ResponseEntity.ok("Ręczne czyszczenie katalogów dla queueType " + queueType + " zostało uruchomione.");
        } catch (NumberFormatException e) {
            return ResponseEntity.status(400).body("Nieprawidłowy format queueType: " + queueType);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Błąd podczas ręcznego czyszczenia: " + e.getMessage());
        }
    }
}