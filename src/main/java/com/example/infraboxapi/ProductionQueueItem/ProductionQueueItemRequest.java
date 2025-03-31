package com.example.infraboxapi.ProductionQueueItemService;

import lombok.Data;

@Data
public class ProductionQueueItemRequest {
    private String partName;
    private String orderName;
    private int quantity;
    private String type;
    private String subtype;
    private String baseCamTime;
    private String camTime;
    private String deadline;
    private String additionalInfo;
    private String fileDirectory;
    private String author;
}