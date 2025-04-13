package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProductionQueueItemService {

    private final ProductionQueueItemRepository productionQueueItemRepository;
    private final ProductionFileInfoService productionFileInfoService;
    private final MachineRepository machineRepository;

    @Autowired
    public ProductionQueueItemService(
            ProductionQueueItemRepository productionQueueItemRepository,
            ProductionFileInfoService productionFileInfoService,
            MachineRepository machineRepository) {
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.productionFileInfoService = productionFileInfoService;
        this.machineRepository = machineRepository;
    }

    @Transactional
    public ProductionQueueItem save(ProductionQueueItem item, List<MultipartFile> files) throws IOException {
        if (item.getQueueType() == null || item.getQueueType().isEmpty()) {
            item.setQueueType("ncQueue");
        }

        if (item.getOrder() == null) {
            Integer maxOrder = productionQueueItemRepository.findMaxOrderByQueueType(item.getQueueType());
            item.setOrder(maxOrder != null ? maxOrder + 1 : 1);
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

        syncAttachmentsToMachinePath(savedItem);

        return savedItem;
    }

    public Optional<ProductionQueueItem> findById(Integer id) {
        return productionQueueItemRepository.findById(id);
    }

    public List<ProductionQueueItem> findAll() {
        return productionQueueItemRepository.findAll();
    }

    @Transactional
    public ProductionQueueItem update(Integer id, ProductionQueueItem updatedItem, List<MultipartFile> files) throws IOException {
        Optional<ProductionQueueItem> existingItemOpt = productionQueueItemRepository.findById(id);
        if (existingItemOpt.isPresent()) {
            ProductionQueueItem existingItem = existingItemOpt.get();
            String oldQueueType = existingItem.getQueueType();

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
            existingItem.setOrder(updatedItem.getOrder());

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
                existingItem.getFiles().addAll(fileInfos);
                productionFileInfoService.saveAll(fileInfos);
            }

            ProductionQueueItem savedItem = productionQueueItemRepository.save(existingItem);

            syncAttachmentsToMachinePath(savedItem);

            if (oldQueueType != null && !oldQueueType.equals(savedItem.getQueueType()) && !"ncQueue".equals(oldQueueType) && !"completed".equals(oldQueueType)) {
                deleteAttachmentsFromMachinePath(savedItem, oldQueueType, savedItem.getQueueType());
            }

            return savedItem;
        } else {
            throw new RuntimeException("ProductionQueueItem with ID " + id + " not found");
        }
    }

    @Transactional
    public void deleteById(Integer id) {
        Optional<ProductionQueueItem> itemOpt = productionQueueItemRepository.findById(id);
        if (itemOpt.isPresent()) {
            ProductionQueueItem item = itemOpt.get();
            deleteAttachmentsFromMachinePath(item);
            productionQueueItemRepository.deleteById(id);
        }
    }

    public List<ProductionQueueItem> findByQueueType(String queueType) {
        return productionQueueItemRepository.findByQueueType(queueType);
    }

    @Transactional
    public ProductionQueueItem toggleComplete(Integer id) {
        Optional<ProductionQueueItem> itemOpt = productionQueueItemRepository.findById(id);
        if (itemOpt.isPresent()) {
            ProductionQueueItem item = itemOpt.get();
            item.setCompleted(!item.isCompleted());
            ProductionQueueItem savedItem = productionQueueItemRepository.save(item);
            syncAttachmentsToMachinePath(savedItem);
            return savedItem;
        } else {
            throw new RuntimeException("ProductionQueueItem with ID " + id + " not found");
        }
    }

    @Transactional
    public void updateQueueOrder(String queueType, List<OrderItem> items) {
        List<Integer> itemIds = items.stream()
                .map(OrderItem::getId)
                .collect(Collectors.toList());
        List<ProductionQueueItem> existingItems = productionQueueItemRepository.findAllById(itemIds);
        Map<Integer, ProductionQueueItem> itemMap = existingItems.stream()
                .collect(Collectors.toMap(ProductionQueueItem::getId, item -> item));

        List<ProductionQueueItem> toUpdate = new ArrayList<>();
        Map<Integer, String> oldQueueTypes = new HashMap<>();

        for (OrderItem orderItem : items) {
            ProductionQueueItem item = itemMap.get(orderItem.getId());
            if (item == null) {
                throw new IllegalArgumentException("Item not found: " + orderItem.getId());
            }
            oldQueueTypes.put(item.getId(), item.getQueueType());
            item.setOrder(orderItem.getOrder());
            item.setQueueType(queueType);
            toUpdate.add(item);
        }

        productionQueueItemRepository.saveAll(toUpdate);

        for (ProductionQueueItem item : toUpdate) {
            System.out.println("Syncing attachments for item ID: " + item.getId() + ", new queueType: " + queueType);
            syncAttachmentsToMachinePath(item);
            String oldQueueType = oldQueueTypes.get(item.getId());
            if (oldQueueType != null && !oldQueueType.equals(queueType) && !"ncQueue".equals(oldQueueType) && !"completed".equals(oldQueueType)) {
                System.out.println("Checking deletion for item ID: " + item.getId() + ", oldQueueType: " + oldQueueType);
                deleteAttachmentsFromMachinePath(item, oldQueueType, queueType);
            }
        }
    }

    private void validateQueueType(String queueType) {
        if (queueType == null || queueType.isEmpty()) {
            return;
        }
        List<String> validQueueTypes = Arrays.asList("ncQueue", "completed");
        if (validQueueTypes.contains(queueType)) {
            return;
        }
        boolean isValidMachine = machineRepository.existsById(Integer.parseInt(queueType));
        if (!isValidMachine) {
            throw new IllegalArgumentException("Invalid queueType: " + queueType);
        }
    }

    private void syncAttachmentsToMachinePath(ProductionQueueItem item) {
        try {
            String queueType = item.getQueueType();
            System.out.println("syncAttachmentsToMachinePath called for item ID: " + item.getId() + ", queueType: " + queueType);
            if (queueType == null || "ncQueue".equals(queueType) || "completed".equals(queueType)) {
                System.out.println("Skipping sync for ncQueue or completed: " + queueType);
                return;
            }

            Optional<Machine> machineOpt = machineRepository.findById(Integer.parseInt(queueType));
            if (machineOpt.isEmpty()) {
                throw new FileOperationException("Machine with ID " + queueType + " not found");
            }

            Machine machine = machineOpt.get();
            String programPath = machine.getProgramPath();
            System.out.println("Program path: " + programPath);

            String orderName = sanitizeFileName(item.getOrderName());
            String partName = sanitizeFileName(item.getPartName());
            System.out.println("OrderName: " + orderName + ", PartName: " + partName);

            Path basePath = Paths.get(programPath, orderName, partName);
            System.out.println("Creating directories at: " + basePath);
            Files.createDirectories(basePath);

            Set<String> existingFiles = Files.exists(basePath)
                    ? Files.list(basePath)
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet())
                    : Collections.emptySet();
            System.out.println("Existing files on disk: " + existingFiles);

            Set<String> appFiles = item.getFiles() == null
                    ? Collections.emptySet()
                    : item.getFiles().stream()
                    .map(file -> sanitizeFileName(file.getFileName()))
                    .collect(Collectors.toSet());
            System.out.println("App files: " + appFiles);

            for (String diskFile : existingFiles) {
                if (!appFiles.contains(diskFile)) {
                    Path filePath = basePath.resolve(diskFile);
                    System.out.println("Deleting file: " + filePath);
                    Files.deleteIfExists(filePath);
                }
            }

            if (item.getFiles() != null && !item.getFiles().isEmpty()) {
                for (ProductionFileInfo file : item.getFiles()) {
                    String fileName = sanitizeFileName(file.getFileName());
                    Path filePath = getUniqueFilePath(basePath, fileName);
                    System.out.println("Writing file: " + filePath);

                    Path tempFile = basePath.resolve(fileName + ".tmp");
                    Files.write(tempFile, file.getFileContent());
                    Files.move(tempFile, filePath, StandardCopyOption.ATOMIC_MOVE);
                    System.out.println("File written successfully: " + filePath);
                }
            }

            if (item.getFiles() == null || item.getFiles().isEmpty()) {
                System.out.println("No attachments, checking partName directory: " + basePath);
                if (isDirectoryEmpty(basePath)) {
                    System.out.println("Removing empty partName directory: " + basePath);
                    deleteDirectoryRecursively(basePath);
                }

                Path orderPath = Paths.get(programPath, orderName);
                if (isDirectoryEmpty(orderPath)) {
                    System.out.println("Removing empty orderName directory: " + orderPath);
                    deleteDirectoryRecursively(orderPath);
                }
            }

        } catch (IOException e) {
            System.err.println("Error in syncAttachmentsToMachinePath for item ID: " + item.getId() + ": " + e.getMessage());
            throw new FileOperationException("Failed to sync attachments to machine path: " + e.getMessage(), e);
        }
    }

    private void deleteAttachmentsFromMachinePath(ProductionQueueItem item, String oldQueueType, String newQueueType) {
        try {
            System.out.println("deleteAttachmentsFromMachinePath called for item ID: " + item.getId() +
                    ", oldQueueType: " + oldQueueType + ", newQueueType: " + newQueueType);

            if (oldQueueType == null || "ncQueue".equals(oldQueueType) || "completed".equals(oldQueueType)) {
                System.out.println("Skipping deletion for oldQueueType: " + oldQueueType);
                return;
            }

            Optional<Machine> oldMachineOpt = machineRepository.findById(Integer.parseInt(oldQueueType));
            if (oldMachineOpt.isEmpty()) {
                System.out.println("Old machine not found for queueType: " + oldQueueType);
                return;
            }

            if ("ncQueue".equals(newQueueType) || "completed".equals(newQueueType)) {
                System.out.println("New queueType is ncQueue or completed, proceeding with deletion for old machine");
            } else {
                try {
                    Optional<Machine> newMachineOpt = machineRepository.findById(Integer.parseInt(newQueueType));
                    if (newMachineOpt.isEmpty()) {
                        System.out.println("New machine not found for queueType: " + newQueueType);
                        return;
                    }

                    Machine oldMachine = oldMachineOpt.get();
                    Machine newMachine = newMachineOpt.get();
                    String oldProgramPath = oldMachine.getProgramPath();
                    String newProgramPath = newMachine.getProgramPath();
                    System.out.println("Old program path: " + oldProgramPath + ", New program path: " + newProgramPath);

                    if (oldProgramPath.equals(newProgramPath)) {
                        System.out.println("Skipping deletion because old and new programPath are the same: " + oldProgramPath);
                        return;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Invalid newQueueType format: " + newQueueType + ", proceeding with deletion for old machine");
                }
            }

            Machine oldMachine = oldMachineOpt.get();
            String oldProgramPath = oldMachine.getProgramPath();
            String orderName = sanitizeFileName(item.getOrderName());
            String partName = sanitizeFileName(item.getPartName());

            Path partPath = Paths.get(oldProgramPath, orderName, partName);
            Path orderPath = Paths.get(oldProgramPath, orderName);
            System.out.println("Part path to delete: " + partPath);

            deleteDirectoryRecursively(partPath);

            if (Files.exists(orderPath) && isDirectoryEmpty(orderPath)) {
                System.out.println("Order path is empty, deleting: " + orderPath);
                deleteDirectoryRecursively(orderPath);
            } else {
                System.out.println("Order path is not empty or does not exist: " + orderPath);
            }

        } catch (IOException e) {
            System.err.println("Failed to delete old attachments for item ID: " + item.getId() + ": " + e.getMessage());
            throw new FileOperationException("Failed to delete old attachments: " + e.getMessage(), e);
        }
    }

    private void deleteAttachmentsFromMachinePath(ProductionQueueItem item) {
        try {
            String queueType = item.getQueueType();
            System.out.println("deleteAttachmentsFromMachinePath (single) called for item ID: " + item.getId() + ", queueType: " + queueType);
            if (queueType == null || "ncQueue".equals(queueType) || "completed".equals(queueType)) {
                System.out.println("Skipping delete for ncQueue or completed: " + queueType);
                return;
            }

            Optional<Machine> machineOpt = machineRepository.findById(Integer.parseInt(queueType));
            if (machineOpt.isEmpty()) {
                System.out.println("Machine not found for queueType: " + queueType);
                return;
            }

            Machine machine = machineOpt.get();
            String programPath = machine.getProgramPath();
            String orderName = sanitizeFileName(item.getOrderName());
            String partName = sanitizeFileName(item.getPartName());

            Path partPath = Paths.get(programPath, orderName, partName);
            Path orderPath = Paths.get(programPath, orderName);
            System.out.println("Part path to delete: " + partPath);

            deleteDirectoryRecursively(partPath);

            if (Files.exists(orderPath) && isDirectoryEmpty(orderPath)) {
                System.out.println("Order path is empty, deleting: " + orderPath);
                deleteDirectoryRecursively(orderPath);
            } else {
                System.out.println("Order path is not empty or does not exist: " + orderPath);
            }

        } catch (IOException e) {
            System.err.println("Failed to delete attachments for item ID: " + item.getId() + ": " + e.getMessage());
            throw new FileOperationException("Failed to delete attachments: " + e.getMessage(), e);
        }
    }

    // Sanitizacja nazw plików i katalogów
    private String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        return name.trim()
                .replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    // Rozwiązywanie konfliktów nazw plików
    private Path getUniqueFilePath(Path basePath, String fileName) throws IOException {
        Path filePath = basePath.resolve(fileName);
        if (!Files.exists(filePath)) {
            return filePath;
        }
        String nameWithoutExt = fileName.replaceFirst("(\\.[^\\.]+)$", "");
        String ext = fileName.substring(fileName.lastIndexOf("."));
        int version = 1;
        while (true) {
            String versionedName = String.format("%s_v%d%s", nameWithoutExt, version, ext);
            filePath = basePath.resolve(versionedName);
            if (!Files.exists(filePath)) {
                return filePath;
            }
            version++;
            if (version > 1000) {
                throw new FileOperationException("Cannot find unique file name for: " + fileName);
            }
        }
    }

    // Sprawdzanie, czy katalog jest pusty
    private boolean isDirectoryEmpty(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return !entries.findFirst().isPresent();
        }
    }

    // Rekursywne usuwanie katalogów
    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            System.out.println("Path does not exist, skipping deletion: " + path);
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                System.out.println("Deleted file: " + file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                System.out.println("Deleted directory: " + dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // Niestandardowy wyjątek dla operacji na plikach
    private static class FileOperationException extends RuntimeException {
        public FileOperationException(String message) {
            super(message);
        }

        public FileOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}