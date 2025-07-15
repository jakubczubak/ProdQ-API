package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
import com.example.infraboxapi.common.CommonService;
import com.example.infraboxapi.materialType.MaterialType;
import com.example.infraboxapi.materialType.MaterialTypeRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/production-queue-item")
public class ProductionQueueItemController {

    private final ProductionQueueItemService productionQueueItemService;
    private final ProductionFileInfoService productionFileInfoService;
    private final CommonService commonService;
    private final MaterialTypeRepository materialTypeRepository;

    @Autowired
    public ProductionQueueItemController(
            ProductionQueueItemService productionQueueItemService,
            ProductionFileInfoService productionFileInfoService,
            CommonService commonService,
            MaterialTypeRepository materialTypeRepository) {
        this.productionQueueItemService = productionQueueItemService;
        this.productionFileInfoService = productionFileInfoService;
        this.commonService = commonService;
        this.materialTypeRepository = materialTypeRepository;
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
        String fileOrderMapping = request.getFileOrderMapping();

        MaterialType materialType = null;
        if (request.getMaterialTypeId() != null) {
            materialType = materialTypeRepository.findById(request.getMaterialTypeId())
                    .orElseThrow(() -> new IllegalArgumentException("MaterialType with ID " + request.getMaterialTypeId() + " not found"));
        }

        ProductionQueueItem item = ProductionQueueItem.builder()
                .type(request.getType())
                .subtype(request.getSubtype())
                .orderName(request.getOrderName())
                .partName(request.getPartName())
                .quantity(request.getQuantity())
                .baseCamTime(request.getBaseCamTime())
                .camTime(request.getCamTime())
                .deadline(request.getDeadline())
                .selectedDays(request.getSelectedDays())
                .additionalInfo(request.getAdditionalInfo())
                .fileDirectory(request.getFileDirectory())
                .completed(request.isCompleted())
                .queueType(request.getQueueType() != null ? request.getQueueType() : "ncQueue")
                .order(request.getOrder())
                .material(request.getMaterial())
                .materialValue(request.getMaterialValue())
                .materialProfile(request.getMaterialProfile())
                .materialType(materialType)
                .materialPricePerKg(request.getMaterialPricePerKg())
                .x(request.getX())
                .y(request.getY())
                .z(request.getZ())
                .diameter(request.getDiameter())
                .innerDiameter(request.getInnerDiameter())
                .length(request.getLength())
                .build();

        ProductionQueueItem savedItem = productionQueueItemService.save(item, files, fileOrderMapping);
        return ResponseEntity.ok(savedItem);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductionQueueItem> getProductionQueueItem(@PathVariable Integer id) {
        return productionQueueItemService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<?> getAllProductionQueueItems(
            @RequestParam(value = "queueType", required = false) String queueType, Pageable pageable) {

        if (queueType != null && !queueType.isEmpty()) {
            Page<ProductionQueueItem> items = productionQueueItemService.findByQueueType(queueType, pageable);
            return ResponseEntity.ok(items);
        } else {
            // This behavior might be deprecated if not used elsewhere
            List<ProductionQueueItem> items = productionQueueItemService.findAll();
            return ResponseEntity.ok(items);
        }
    }

    @GetMapping("/nc-queue")
    public ResponseEntity<Page<ProductionQueueItem>> getNcQueueItems(Pageable pageable) {
        Page<ProductionQueueItem> items = productionQueueItemService.findByQueueType("ncQueue", pageable);
        return ResponseEntity.ok(items);
    }

    @GetMapping("/completed")
    public ResponseEntity<Page<ProductionQueueItem>> getCompletedItems(Pageable pageable) {
        Page<ProductionQueueItem> items = productionQueueItemService.findByQueueType("completed", pageable);
        return ResponseEntity.ok(items);
    }

    @GetMapping("/machine/{machineId}")
    public ResponseEntity<Page<ProductionQueueItem>> getMachineQueueItems(@PathVariable Integer machineId, Pageable pageable) {
        Page<ProductionQueueItem> items = productionQueueItemService.findByQueueType(String.valueOf(machineId), pageable);
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

        MaterialType materialType = null;
        if (request.getMaterialTypeId() != null) {
            materialType = materialTypeRepository.findById(request.getMaterialTypeId())
                    .orElseThrow(() -> new IllegalArgumentException("MaterialType with ID " + request.getMaterialTypeId() + " not found"));
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
                .selectedDays(request.getSelectedDays())
                .additionalInfo(request.getAdditionalInfo())
                .fileDirectory(request.getFileDirectory())
                .queueType(request.getQueueType())
                .completed(request.isCompleted())
                .order(request.getOrder())
                .material(request.getMaterial())
                .materialValue(request.getMaterialValue())
                .materialProfile(request.getMaterialProfile())
                .materialType(materialType)
                .materialPricePerKg(request.getMaterialPricePerKg())
                .x(request.getX())
                .y(request.getY())
                .z(request.getZ())
                .diameter(request.getDiameter())
                .innerDiameter(request.getInnerDiameter())
                .length(request.getLength())
                .build();

        ProductionQueueItem savedItem = productionQueueItemService.update(id, updatedItem, request.getFile(), request.getFileOrderMapping());
        return ResponseEntity.ok(savedItem);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProductionQueueItem(@PathVariable Integer id) throws IOException {
        productionQueueItemService.deleteById(Math.toIntExact(Long.valueOf(id)));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<byte[]> getFileContent(@PathVariable Long fileId) throws IOException {
        byte[] fileContent = productionQueueItemService.getFileContent(fileId);
        Optional<ProductionFileInfo> fileOpt = productionFileInfoService.findById(fileId);
        if (fileOpt.isPresent()) {
            ProductionFileInfo file = fileOpt.get();
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + file.getFileName())
                    .contentType(MediaType.parseMediaType(file.getFileType()))
                    .body(fileContent);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/toggle-complete")
    public ResponseEntity<ProductionQueueItem> toggleComplete(@PathVariable Integer id) throws IOException {
        ProductionQueueItem updatedItem = productionQueueItemService.toggleComplete(id);
        return ResponseEntity.ok(updatedItem);
    }

    @PutMapping("/update-order")
    public ResponseEntity<String> updateQueueOrder(@RequestBody UpdateQueueOrderRequest request) throws IOException {
        productionQueueItemService.updateQueueOrder(request.getQueueType(), request.getItems());
        return ResponseEntity.ok("{\"success\": true}");
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long fileId) {
        productionFileInfoService.deleteById(fileId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync-with-machine")
    public ResponseEntity<List<ProductionQueueItem>> syncWithMachine(@RequestParam String queueType) throws IOException {
        if (queueType == null || queueType.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }
        productionQueueItemService.syncWithMachine(queueType);
        Page<ProductionQueueItem> itemsPage = productionQueueItemService.findByQueueType(queueType, Pageable.unpaged());
        return ResponseEntity.ok(itemsPage.getContent());
    }

    @PatchMapping("/move-completed/{machineId}")
    public ResponseEntity<List<ProductionQueueItem>> moveCompletedPrograms(@PathVariable Integer machineId) throws IOException {
        List<ProductionQueueItem> updatedItems = productionQueueItemService.moveCompletedPrograms(machineId);
        return ResponseEntity.ok(updatedItems);
    }
}