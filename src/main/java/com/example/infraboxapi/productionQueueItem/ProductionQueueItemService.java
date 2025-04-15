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
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProductionQueueItemService {

    private final ProductionQueueItemRepository productionQueueItemRepository;
    private final ProductionFileInfoService productionFileInfoService;
    private final MachineRepository machineRepository;
    private final MachineQueueFileGeneratorService machineQueueFileGeneratorService;

    @Autowired
    public ProductionQueueItemService(
            ProductionQueueItemRepository productionQueueItemRepository,
            ProductionFileInfoService productionFileInfoService,
            MachineRepository machineRepository,
            MachineQueueFileGeneratorService machineQueueFileGeneratorService) {
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.productionFileInfoService = productionFileInfoService;
        this.machineRepository = machineRepository;
        this.machineQueueFileGeneratorService = machineQueueFileGeneratorService;
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
                String sanitizedFileName = sanitizeFileName(file.getOriginalFilename());
                ProductionFileInfo fileInfo = ProductionFileInfo.builder()
                        .fileName(sanitizedFileName)
                        .fileType(file.getContentType())
                        .fileContent(file.getBytes())
                        .productionQueueItem(savedItem)
                        .completed(false)
                        .build();
                fileInfos.add(fileInfo);
            }
            savedItem.setFiles(fileInfos);
            productionFileInfoService.saveAll(fileInfos);
        }

        savedItem.setCompleted(checkAllMpfCompleted(savedItem));
        productionQueueItemRepository.save(savedItem);

        syncAttachmentsToMachinePath(savedItem);
        machineQueueFileGeneratorService.generateQueueFileForMachine(savedItem.getQueueType());

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
            existingItem.setOrder(updatedItem.getOrder());

            String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            existingItem.setAuthor(currentUserEmail);

            if (files != null && !files.isEmpty()) {
                List<ProductionFileInfo> fileInfos = new ArrayList<>();
                for (MultipartFile file : files) {
                    String sanitizedFileName = sanitizeFileName(file.getOriginalFilename());
                    ProductionFileInfo fileInfo = ProductionFileInfo.builder()
                            .fileName(sanitizedFileName)
                            .fileType(file.getContentType())
                            .fileContent(file.getBytes())
                            .productionQueueItem(existingItem)
                            .completed(false)
                            .build();
                    fileInfos.add(fileInfo);
                }
                existingItem.getFiles().addAll(fileInfos);
                productionFileInfoService.saveAll(fileInfos);
            }

            existingItem.setCompleted(checkAllMpfCompleted(existingItem));
            ProductionQueueItem savedItem = productionQueueItemRepository.save(existingItem);

            syncAttachmentsToMachinePath(savedItem);

            if (oldQueueType != null && !oldQueueType.equals(savedItem.getQueueType()) && !"ncQueue".equals(oldQueueType) && !"completed".equals(oldQueueType)) {
                deleteAttachmentsFromMachinePath(savedItem, oldQueueType, savedItem.getQueueType());
                machineQueueFileGeneratorService.generateQueueFileForMachine(oldQueueType);
            }
            machineQueueFileGeneratorService.generateQueueFileForMachine(savedItem.getQueueType());

            return savedItem;
        } else {
            throw new RuntimeException("ProductionQueueItem with ID " + id + " not found");
        }
    }

    @Transactional
    public void deleteById(Integer id) throws IOException {
        Optional<ProductionQueueItem> itemOpt = productionQueueItemRepository.findById(id);
        if (itemOpt.isPresent()) {
            ProductionQueueItem item = itemOpt.get();
            String queueType = item.getQueueType();
            deleteAttachmentsFromMachinePath(item);
            productionQueueItemRepository.deleteById(id);
            machineQueueFileGeneratorService.generateQueueFileForMachine(queueType);
        }
    }

    public List<ProductionQueueItem> findByQueueType(String queueType) {
        return productionQueueItemRepository.findByQueueType(queueType);
    }

    @Transactional
    public ProductionQueueItem toggleComplete(Integer id) throws IOException {
        Optional<ProductionQueueItem> itemOpt = productionQueueItemRepository.findById(id);
        if (itemOpt.isPresent()) {
            ProductionQueueItem item = itemOpt.get();
            boolean newCompletedStatus = !item.isCompleted();
            item.setCompleted(newCompletedStatus);

            if (item.getFiles() != null) {
                for (ProductionFileInfo file : item.getFiles()) {
                    if (file.getFileName().toLowerCase().endsWith(".mpf")) {
                        file.setCompleted(newCompletedStatus);
                    }
                }
                productionFileInfoService.saveAll(item.getFiles());
            }

            ProductionQueueItem savedItem = productionQueueItemRepository.save(item);
            syncAttachmentsToMachinePath(savedItem);
            machineQueueFileGeneratorService.generateQueueFileForMachine(savedItem.getQueueType());
            return savedItem;
        } else {
            throw new RuntimeException("ProductionQueueItem with ID " + id + " not found");
        }
    }

    @Transactional
    public void updateQueueOrder(String queueType, List<OrderItem> items) throws IOException {
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

        Set<String> queueTypesToUpdate = new HashSet<>();
        queueTypesToUpdate.add(queueType);
        queueTypesToUpdate.addAll(oldQueueTypes.values());

        for (ProductionQueueItem item : toUpdate) {
            syncAttachmentsToMachinePath(item);
            String oldQueueType = oldQueueTypes.get(item.getId());
            if (oldQueueType != null && !oldQueueType.equals(queueType) && !"ncQueue".equals(oldQueueType) && !"completed".equals(oldQueueType)) {
                deleteAttachmentsFromMachinePath(item, oldQueueType, queueType);
            }
        }

        for (String qt : queueTypesToUpdate) {
            machineQueueFileGeneratorService.generateQueueFileForMachine(qt);
        }
    }

    private boolean checkAllMpfCompleted(ProductionQueueItem item) {
        if (item.getFiles() == null || item.getFiles().isEmpty()) {
            return false;
        }
        return item.getFiles().stream()
                .filter(f -> f.getFileName().toLowerCase().endsWith(".mpf"))
                .allMatch(ProductionFileInfo::isCompleted);
    }

    private void validateQueueType(String queueType) {
        if (queueType == null || queueType.isEmpty()) {
            return;
        }
        List<String> validQueueTypes = Arrays.asList("ncQueue", "completed");
        if (validQueueTypes.contains(queueType)) {
            return;
        }
        try {
            boolean isValidMachine = machineRepository.existsById(Integer.parseInt(queueType));
            if (!isValidMachine) {
                throw new IllegalArgumentException("Invalid queueType: " + queueType);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid queueType: " + queueType);
        }
    }

    private void syncAttachmentsToMachinePath(ProductionQueueItem item) {
        try {
            String queueType = item.getQueueType();
            if (queueType == null || "ncQueue".equals(queueType) || "completed".equals(queueType)) {
                return;
            }

            Optional<Machine> machineOpt = machineRepository.findById(Integer.parseInt(queueType));
            if (machineOpt.isEmpty()) {
                throw new FileOperationException("Machine with ID " + queueType + " not found");
            }

            Machine machine = machineOpt.get();
            String programPath = machine.getProgramPath();

            String orderName = sanitizeFileName(item.getOrderName(), "NoOrderName_" + item.getId());
            String partName = sanitizeFileName(item.getPartName(), "NoPartName_" + item.getId());

            Path basePath = Paths.get(programPath, orderName, partName);
            Files.createDirectories(basePath);

            Set<String> existingFiles = Files.exists(basePath)
                    ? Files.list(basePath)
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet())
                    : Collections.emptySet();

            Set<String> appFiles = item.getFiles() == null
                    ? Collections.emptySet()
                    : item.getFiles().stream()
                    .map(ProductionFileInfo::getFileName)
                    .collect(Collectors.toSet());

            for (String diskFile : existingFiles) {
                if (!appFiles.contains(diskFile)) {
                    Path filePath = basePath.resolve(diskFile);
                    Files.deleteIfExists(filePath);
                }
            }

            if (item.getFiles() != null && !item.getFiles().isEmpty()) {
                for (ProductionFileInfo file : item.getFiles()) {
                    String fileName = file.getFileName();
                    Path filePath = getUniqueFilePath(basePath, fileName);

                    Path tempFile = basePath.resolve(fileName + ".tmp");
                    Files.write(tempFile, file.getFileContent());
                    Files.move(tempFile, filePath, StandardCopyOption.ATOMIC_MOVE);
                }
            }

            if (item.getFiles() == null || item.getFiles().isEmpty()) {
                if (isDirectoryEmpty(basePath)) {
                    deleteDirectoryRecursively(basePath);
                }

                Path orderPath = Paths.get(programPath, orderName);
                if (isDirectoryEmpty(orderPath)) {
                    deleteDirectoryRecursively(orderPath);
                }
            }

        } catch (IOException e) {
            throw new FileOperationException("Failed to sync attachments to machine path: " + e.getMessage(), e);
        }
    }

    private void deleteAttachmentsFromMachinePath(ProductionQueueItem item, String oldQueueType, String newQueueType) {
        try {
            if (oldQueueType == null || "ncQueue".equals(oldQueueType) || "completed".equals(oldQueueType)) {
                return;
            }

            Optional<Machine> oldMachineOpt = machineRepository.findById(Integer.parseInt(oldQueueType));
            if (oldMachineOpt.isEmpty()) {
                return;
            }

            if ("ncQueue".equals(newQueueType) || "completed".equals(newQueueType)) {
                // Proceed with deletion
            } else {
                try {
                    Optional<Machine> newMachineOpt = machineRepository.findById(Integer.parseInt(newQueueType));
                    if (newMachineOpt.isEmpty()) {
                        return;
                    }

                    Machine oldMachine = oldMachineOpt.get();
                    Machine newMachine = newMachineOpt.get();
                    String oldProgramPath = oldMachine.getProgramPath();
                    String newProgramPath = newMachine.getProgramPath();

                    if (oldProgramPath.equals(newProgramPath)) {
                        return;
                    }
                } catch (NumberFormatException e) {
                    // newQueueType is not a machine ID, proceed with deletion
                }
            }

            Machine oldMachine = oldMachineOpt.get();
            String oldProgramPath = oldMachine.getProgramPath();
            String orderName = sanitizeFileName(item.getOrderName(), "NoOrderName_" + item.getId());
            String partName = sanitizeFileName(item.getPartName(), "NoPartName_" + item.getId());

            Path partPath = Paths.get(oldProgramPath, orderName, partName);
            Path orderPath = Paths.get(oldProgramPath, orderName);

            deleteDirectoryRecursively(partPath);

            if (Files.exists(orderPath) && isDirectoryEmpty(orderPath)) {
                deleteDirectoryRecursively(orderPath);
            }

        } catch (IOException e) {
            throw new FileOperationException("Failed to delete old attachments: " + e.getMessage(), e);
        }
    }

    private void deleteAttachmentsFromMachinePath(ProductionQueueItem item) {
        try {
            String queueType = item.getQueueType();
            if (queueType == null || "ncQueue".equals(queueType) || "completed".equals(queueType)) {
                return;
            }

            Optional<Machine> machineOpt = machineRepository.findById(Integer.parseInt(queueType));
            if (machineOpt.isEmpty()) {
                return;
            }

            Machine machine = machineOpt.get();
            String programPath = machine.getProgramPath();
            String orderName = sanitizeFileName(item.getOrderName(), "NoOrderName_" + item.getId());
            String partName = sanitizeFileName(item.getPartName(), "NoPartName_" + item.getId());

            Path partPath = Paths.get(programPath, orderName, partName);
            Path orderPath = Paths.get(programPath, orderName);

            deleteDirectoryRecursively(partPath);

            if (Files.exists(orderPath) && isDirectoryEmpty(orderPath)) {
                deleteDirectoryRecursively(orderPath);
            }

        } catch (IOException e) {
            throw new FileOperationException("Failed to delete attachments: " + e.getMessage(), e);
        }
    }

    private String sanitizeFileName(String name, String defaultName) {
        if (name == null || name.trim().isEmpty()) {
            return defaultName;
        }
        // Normalizuj znaki i usuń polskie diakrytyki
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        // Zastąp specyficzne polskie znaki
        normalized = normalized.replaceAll("[ąĄ]", "a")
                .replaceAll("[ćĆ]", "c")
                .replaceAll("[ęĘ]", "e")
                .replaceAll("[łŁ]", "l")
                .replaceAll("[ńŃ]", "n")
                .replaceAll("[óÓ]", "o")
                .replaceAll("[śŚ]", "s")
                .replaceAll("[źŹ]", "z")
                .replaceAll("[żŻ]", "z");
        // Usuń niedozwolone znaki
        return normalized.replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_");
    }

    private String sanitizeFileName(String name) {
        return sanitizeFileName(name, "UNKNOWN");
    }

    private Path getUniqueFilePath(Path basePath, String fileName) throws IOException {
        Path filePath = basePath.resolve(fileName);
        if (!Files.exists(filePath)) {
            return filePath;
        }
        String nameWithoutExt = fileName.replaceFirst("(\\.[^\\.]+)$", "");
        String ext = fileName.substring(fileName.lastIndexOf("."));
        int version = 1;
        while (true) {
            String versionedName = String.format("%s_%d%s", nameWithoutExt, version, ext);
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

    private boolean isDirectoryEmpty(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return !entries.findFirst().isPresent();
        }
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static class FileOperationException extends RuntimeException {
        public FileOperationException(String message) {
            super(message);
        }

        public FileOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}