package com.example.infraboxapi.material;

import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@EnableScheduling
public class MaterialScannerService {

    private final NotificationService notificationService;
    private final MaterialRepository materialRepository;

    public MaterialScannerService(NotificationService notificationService, MaterialRepository materialRepository) {
        this.notificationService = notificationService;
        this.materialRepository = materialRepository;
    }

    @Scheduled(fixedRate = 86400000) // Uruchom co 24 godziny (1 dzień)
    public void scanMaterialsAndNotify() {
        List<Material> materials = materialRepository.findAll();

        for (Material material : materials) {
            if (material.getQuantity() < material.getMinQuantity()) {
                String description = "Material " + material.getName() + " is running low. There are " + material.getQuantity() + " pieces left.";
                notificationService.createAndSendSystemNotification(description, NotificationDescription.MaterialScanner);
            }
        }
    }
}
