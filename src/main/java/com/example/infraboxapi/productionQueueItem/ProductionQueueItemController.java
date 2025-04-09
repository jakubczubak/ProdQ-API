package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
import com.example.infraboxapi.common.CommonService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/production-queue-item")
public class ProductionQueueItemController {

    private final ProductionQueueItemService productionQueueItemService;
    private final ProductionFileInfoService productionFileInfoService;
    private final CommonService commonService;

    @Autowired
    public ProductionQueueItemController(
            ProductionQueueItemService productionQueueItemService,
            ProductionFileInfoService productionFileInfoService,
            CommonService commonService) {
        this.productionQueueItemService = productionQueueItemService;
        this.productionFileInfoService = productionFileInfoService;
        this.commonService = commonService;
    }

    @PostMapping(value = "/add", consumes = {"multipart/form-data"})
    public ResponseEntity<ProductionQueueItem> addProductionQueueItem(
            @Valid @ModelAttribute ProductionQueueItemRequest request,
            BindingResult bindingResult) throws IOException {
        if (bindingResult.hasErrors()) {
            ResponseEntity<String> errorResponse = commonService.handleBindingResult(bindingResult);
            return ResponseEntity.status(errorResponse.getStatusCode()).body(null);
        }

        List<MultipartFile> files = request.getFile();

        ProductionQueueItem item = ProductionQueueItem.builder()
                .type(request.getType())
                .subtype(request.getSubtype())
                .orderName(request.getOrderName())
                .partName(request.getPartName())
                .quantity(request.getQuantity())
                .baseCamTime(request.getBaseCamTime())
                .camTime(request.getCamTime())
                .deadline(request.getDeadline())
                .additionalInfo(request.getAdditionalInfo())
                .fileDirectory(request.getFileDirectory())
                .completed(request.isCompleted())
                .queueType(request.getQueueType() != null ? request.getQueueType() : "ncQueue")
                .order(request.getOrder())
                .build();

        ProductionQueueItem savedItem = productionQueueItemService.save(item, files);
        return ResponseEntity.ok(savedItem);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductionQueueItem> getProductionQueueItem(@PathVariable Integer id) {
        return productionQueueItemService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<ProductionQueueItem>> getAllProductionQueueItems(
            @RequestParam(value = "queueType", required = false) String queueType) {
        List<ProductionQueueItem> items;
        if (queueType != null && !queueType.isEmpty()) {
            items = productionQueueItemService.findByQueueType(queueType);
        } else {
            items = productionQueueItemService.findAll();
        }
        return ResponseEntity.ok(items);
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<ProductionQueueItem> updateProductionQueueItem(
            @PathVariable Integer id,
            @Valid @ModelAttribute ProductionQueueItemRequest request,
            BindingResult bindingResult) throws IOException {
        if (bindingResult.hasErrors()) {
            ResponseEntity<String> errorResponse = commonService.handleBindingResult(bindingResult);
            return ResponseEntity.status(errorResponse.getStatusCode()).body(null);
        }

        ProductionQueueItem updatedItem = ProductionQueueItem.builder()
                .type(request.getType())
                .subtype(request.getSubtype())
                .orderName(request.getOrderName())
                .partName(request.getPartName())
                .quantity(request.getQuantity())
                .baseCamTime(request.getBaseCamTime())
                .camTime(request.getCamTime())
                .deadline(request.getDeadline())
                .additionalInfo(request.getAdditionalInfo())
                .fileDirectory(request.getFileDirectory())
                .queueType(request.getQueueType())
                .completed(request.isCompleted())
                .order(request.getOrder())
                .build();

        ProductionQueueItem savedItem = productionQueueItemService.update(id, updatedItem, request.getFile());
        return ResponseEntity.ok(savedItem);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProductionQueueItem(@PathVariable Integer id) {
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

    @PatchMapping("/{id}/toggle-complete")
    public ResponseEntity<ProductionQueueItem> toggleComplete(@PathVariable Integer id) {
        ProductionQueueItem updatedItem = productionQueueItemService.toggleComplete(id);
        return ResponseEntity.ok(updatedItem);
    }

    @PutMapping("/update-order")
    public ResponseEntity<String> updateQueueOrder(@RequestBody UpdateQueueOrderRequest request) {
        productionQueueItemService.updateQueueOrder(request.getQueueType(), request.getItems());
        return ResponseEntity.ok("{\"success\": true}"); // Zwracamy JSON z informacją o sukcesie
    }
}