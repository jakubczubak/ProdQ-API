package com.example.infraboxapi.notification;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notification")
@CrossOrigin(origins = "http://localhost:3000")
@AllArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @DeleteMapping("/delete/{id}")
    @Transactional
    public ResponseEntity<String> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.ok("Notification deleted");
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<String> updateNotification(@PathVariable Long id) {
        try {
            notificationService.updateNotification(id);
            return ResponseEntity.ok("Notification updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating notification: " + e.getMessage());
        }
    }
}
