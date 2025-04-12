package com.example.infraboxapi.productionQueueItem;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class ProductionQueueItemRequest {
    @NotBlank(message = "Type cannot be blank")
    private String type;

    @NotBlank(message = "Order name cannot be blank")
    private String orderName;

    private String subtype;

    @NotBlank(message = "Part name cannot be blank")
    private String partName;

    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;

    private String baseCamTime;
    private String camTime;
    private String deadline;
    private String additionalInfo;
    private String fileDirectory;
    private String queueType;
    private String author;
    private boolean completed;
    private Integer order;
    private List<MultipartFile> file;
}