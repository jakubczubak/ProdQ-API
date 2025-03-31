package com.example.infraboxapi.productionQueue;

import com.example.infraboxapi.productionQueueItem.ProductionQueueItem;
import com.example.infraboxapi.productionQueueItem.ProductionQueueItemRequest;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/production-queue")
public class ProductionQueueController {

    private final ProductionQueueService productionQueueService;

    @Autowired
    public ProductionQueueController(
            ProductionQueueService productionQueueService) {
        this.productionQueueService = productionQueueService;
    }

    @GetMapping
    public ResponseEntity<ProductionQueue> getProductionQueue() {
        return ResponseEntity.ok(productionQueueService.getSingleQueue());
    }
}