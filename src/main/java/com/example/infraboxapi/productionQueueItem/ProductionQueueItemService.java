package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
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
        if (item.getQueueType() == null || item.getQueueType().isEmpty()) {
            item.setQueueType("ncQueue");
        }

        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        item.setAuthor(currentUserEmail);

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

    public Optional<ProductionQueueItem> findById(Integer id) {
        return productionQueueItemRepository.findById(id);
    }

    public List<ProductionQueueItem> findAll() {
        return productionQueueItemRepository.findAll();
    }

    public ProductionQueueItem update(Integer id, ProductionQueueItem updatedItem, List<MultipartFile> files) throws IOException {
        Optional<ProductionQueueItem> existingItemOpt = productionQueueItemRepository.findById(id);
        if (existingItemOpt.isPresent()) {
            ProductionQueueItem existingItem = existingItemOpt.get();
            existingItem.setType(updatedItem.getType());
            existingItem.setSubtype(updatedItem.getSubtype());
            existingItem.setOrderName(updatedItem.getOrderName());
            existingItem.setPartName(updatedItem.getPartName());
            existingItem.setQuantity(updatedItem.getQuantity());
            existingItem.setBaseCamTime(updatedItem.getBaseCamTime());
            existingItem.setCamTime(updatedItem.getCamTime());
            existingItem.setDeadline(updatedItem.getDeadline());
            existingItem.setAdditionalInfo(updatedItem.getAdditionalInfo());
            existingItem.setFileDirectory(updatedItem.getFileDirectory());
            existingItem.setQueueType(updatedItem.getQueueType());
            existingItem.setCompleted(updatedItem.isCompleted());

            String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            existingItem.setAuthor(currentUserEmail);

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

    public void deleteById(Integer id) {
        productionQueueItemRepository.deleteById(id);
    }

    public List<ProductionQueueItem> findByQueueType(String queueType) {
        return productionQueueItemRepository.findByQueueType(queueType);
    }

    public ProductionQueueItem toggleComplete(Integer id) {
        Optional<ProductionQueueItem> itemOpt = productionQueueItemRepository.findById(id);
        if (itemOpt.isPresent()) {
            ProductionQueueItem item = itemOpt.get();
            item.setCompleted(!item.isCompleted()); // Przełącz wartość
            return productionQueueItemRepository.save(item);
        } else {
            throw new RuntimeException("ProductionQueueItem with ID " + id + " not found");
        }
    }
}