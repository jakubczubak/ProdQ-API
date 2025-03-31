package com.example.infraboxapi.ProductionQueueItemService;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ProductionQueueItemService {

    private final ProductionQueueItemRepository productionQueueItemRepository;
    private final ProductionFileInfoService productionFileInfoService;

    @Autowired
    public ProductionQueueItemService(
            ProductionQueueItemRepository productionQueueItemRepository,
            ProductionFileInfoService productionFileInfoService) {
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.productionFileInfoService = productionFileInfoService;
    }

    public ProductionQueueItem save(ProductionQueueItem item, List<MultipartFile> files) throws IOException {
        ProductionQueueItem savedItem = productionQueueItemRepository.save(item);

        if (files != null && !files.isEmpty()) {
            List<ProductionFileInfo> fileInfos = new ArrayList<>();
            for (MultipartFile file : files) {
                ProductionFileInfo fileInfo = ProductionFileInfo.builder()
                        .fileName(file.getOriginalFilename())
                        .fileType(file.getContentType())
                        .fileContent(file.getBytes())
                        .productionQueueItem(savedItem)
                        .build();
                fileInfos.add(fileInfo);
            }
            savedItem.setFiles(fileInfos);
            productionFileInfoService.saveAll(fileInfos);
        }

        return savedItem;
    }

    public Optional<ProductionQueueItem> findById(String id) {
        return productionQueueItemRepository.findById(id);
    }

    public List<ProductionQueueItem> findAll() {
        return productionQueueItemRepository.findAll();
    }

    public ProductionQueueItem update(String id, ProductionQueueItem updatedItem, List<MultipartFile> files) throws IOException {
        Optional<ProductionQueueItem> existingItemOpt = productionQueueItemRepository.findById(id);
        if (existingItemOpt.isPresent()) {
            ProductionQueueItem existingItem = existingItemOpt.get();
            existingItem.setPartName(updatedItem.getPartName());
            existingItem.setOrderName(updatedItem.getOrderName());
            existingItem.setQuantity(updatedItem.getQuantity());
            existingItem.setType(updatedItem.getType());
            existingItem.setSubtype(updatedItem.getSubtype());
            existingItem.setBaseCamTime(updatedItem.getBaseCamTime());
            existingItem.setCamTime(updatedItem.getCamTime());
            existingItem.setDeadline(updatedItem.getDeadline());
            existingItem.setAdditionalInfo(updatedItem.getAdditionalInfo());
            existingItem.setFileDirectory(updatedItem.getFileDirectory());
            existingItem.setAuthor(updatedItem.getAuthor());
            existingItem.setCompleted(updatedItem.isCompleted());

            if (files != null && !files.isEmpty()) {
                List<ProductionFileInfo> fileInfos = new ArrayList<>();
                for (MultipartFile file : files) {
                    ProductionFileInfo fileInfo = ProductionFileInfo.builder()
                            .fileName(file.getOriginalFilename())
                            .fileType(file.getContentType())
                            .fileContent(file.getBytes())
                            .productionQueueItem(existingItem)
                            .build();
                    fileInfos.add(fileInfo);
                }
                existingItem.getFiles().clear();
                existingItem.getFiles().addAll(fileInfos);
            }

            return productionQueueItemRepository.save(existingItem);
        } else {
            throw new RuntimeException("ProductionQueueItem with ID " + id + " not found");
        }
    }

    public void deleteById(String id) {
        productionQueueItemRepository.deleteById(id);
    }
}