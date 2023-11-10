package com.example.infraboxapi.tool;

import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@EnableScheduling
public class ToolScannerService {

    private final NotificationService notificationService;
    private final ToolRepository toolRepository;

    public ToolScannerService(NotificationService notificationService, ToolRepository toolRepository) {
        this.notificationService = notificationService;
        this.toolRepository = toolRepository;
    }
    @Scheduled(fixedRate = 86400000) // Uruchom co 24 godziny (1 dzień)
    public void scanToolsAndNotify() {
        List<Tool> tools = toolRepository.findAll();

        for (Tool tool : tools){
            if (tool.getQuantity() < tool.getMinQuantity()){
                String description = "Tool " + tool.getName() + " is running low. There are " + tool.getQuantity() + " pieces left.";
                notificationService.createAndSendSystemNotification(description, NotificationDescription.ToolScanner);
            }
        }
    }
}
