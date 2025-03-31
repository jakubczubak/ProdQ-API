package com.example.infraboxapi.productionQueueItem;

import lombok.Data;

@Data
public class ProductionQueueItemRequest {
    private String type;
    private String subtype;
    private String orderName;
    private String partName;
    private int quantity;
    private String baseCamTime;
    private String camTime;
    private String deadline;
    private String additionalInfo;
    private String fileDirectory;
    private String queueType; // Opcjonalne, domyślnie "ncQueue"
    private String author;    // Added author field (though this might not be needed in request)
}