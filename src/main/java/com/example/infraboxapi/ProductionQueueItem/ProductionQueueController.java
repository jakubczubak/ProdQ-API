package com.example.infraboxapi.ProductionQueueItemService;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/production-queue")
public class ProductionQueueController {

    private final ProductionQueueItemService productionQueueItemService;
    private final ProductionFileInfoService productionFileInfoService;

    @Autowired
    public ProductionQueueController(
            ProductionQueueItemService productionQueueItemService,
            ProductionFileInfoService productionFileInfoService) {
        this.productionQueueItemService = productionQueueItemService;
        this.productionFileInfoService = productionFileInfoService;
    }

    @PostMapping(value = "/add", consumes = {"multipart/form-data"})
    public ResponseEntity<ProductionQueueItem> addProductionQueueItem(
            @RequestPart("data") ProductionQueueItemRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws IOException {

        ProductionQueueItem item = ProductionQueueItem.builder()
                .partName(request.getPartName())
                .orderName(request.getOrderName())
                .quantity(request.getQuantity())
                .type(request.getType())
                .subtype(request.getSubtype())
                .baseCamTime(request.getBaseCamTime())
                .camTime(request.getCamTime())
                .deadline(request.getDeadline())
                .additionalInfo(request.getAdditionalInfo())
                .fileDirectory(request.getFileDirectory())
                .author(request.getAuthor())
                .isCompleted(false)
                .build();

        ProductionQueueItem savedItem = productionQueueItemService.save(item, files);
        return ResponseEntity.ok(savedItem);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductionQueueItem> getProductionQueueItem(@PathVariable String id) {
        return productionQueueItemService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<ProductionQueueItem>> getAllProductionQueueItems() {
        List<ProductionQueueItem> items = productionQueueItemService.findAll();
        return ResponseEntity.ok(items);
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<ProductionQueueItem> updateProductionQueueItem(
            @PathVariable String id,
            @RequestPart("data") ProductionQueueItemRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws IOException {

        ProductionQueueItem updatedItem = ProductionQueueItem.builder()
                .partName(request.getPartName())
                .orderName(request.getOrderName())
                .quantity(request.getQuantity())
                .type(request.getType())
                .subtype(request.getSubtype())
                .baseCamTime(request.getBaseCamTime())
                .camTime(request.getCamTime())
                .deadline(request.getDeadline())
                .additionalInfo(request.getAdditionalInfo())
                .fileDirectory(request.getFileDirectory())
                .author(request.getAuthor())
                .isCompleted(false)
                .build();

        ProductionQueueItem savedItem = productionQueueItemService.update(id, updatedItem, files);
        return ResponseEntity.ok(savedItem);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProductionQueueItem(@PathVariable String id) {
        productionQueueItemService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<byte[]> getFileContent(@PathVariable Long fileId) {
        return productionFileInfoService.findById(fileId)
                .map(file -> ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=" + file.getFileName())
                        .contentType(MediaType.parseMediaType(file.getFileType()))
                        .body(file.getFileContent()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}