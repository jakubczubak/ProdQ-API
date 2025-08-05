package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
import com.example.infraboxapi.user.User;
import com.example.infraboxapi.user.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductionQueueItemService {

    private static final Logger logger = LoggerFactory.getLogger(ProductionQueueItemService.class);

    private final ProductionQueueItemRepository productionQueueItemRepository;
    private final ProductionFileInfoService productionFileInfoService;
    private final MachineRepository machineRepository;
    private final MachineQueueFileGeneratorService machineQueueFileGeneratorService;
    private final FileWatcherService fileWatcherService;
    private final FileSystemService fileSystemService;
    private final UserRepository userRepository;
    private final ToolListGeneratorService toolListGeneratorService; // <-- DODANA ZALEŻNOŚĆ

    @Value("${file.upload-dir:Uploads}")
    private String uploadDir;

    @Autowired
    public ProductionQueueItemService(
            ProductionQueueItemRepository productionQueueItemRepository,
            ProductionFileInfoService productionFileInfoService,
            MachineRepository machineRepository,
            MachineQueueFileGeneratorService machineQueueFileGeneratorService,
            FileWatcherService fileWatcherService,
            FileSystemService fileSystemService,
            UserRepository userRepository,
            ToolListGeneratorService toolListGeneratorService) { // <-- DODANA ZALEŻNOŚĆ
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.productionFileInfoService = productionFileInfoService;
        this.machineRepository = machineRepository;
        this.machineQueueFileGeneratorService = machineQueueFileGeneratorService;
        this.fileWatcherService = fileWatcherService;
        this.fileSystemService = fileSystemService;
        this.userRepository = userRepository;
        this.toolListGeneratorService = toolListGeneratorService; // <-- DODANA ZALEŻNOŚĆ
    }

    @Transactional
    public ProductionQueueItem save(ProductionQueueItem item, List<MultipartFile> files, String fileOrderMapping) throws IOException {
        validateQueueType(item.getQueueType());
        if (item.getQueueType() == null || item.getQueueType().isEmpty()) {
            item.setQueueType("ncQueue");
        }

        if (item.getOrder() == null) {
            Integer maxOrder = productionQueueItemRepository.findMaxOrderByQueueType(item.getQueueType());
            item.setOrder(maxOrder != null ? maxOrder + 1 : 1);
        }

        String sanitizedPartName = fileSystemService.sanitizeName(item.getPartName(), "NoPartName_" + System.currentTimeMillis());
        sanitizedPartName = getUniquePartName(item.getQueueType(), sanitizedPartName);
        item.setPartName(sanitizedPartName);

        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        String author = getUserFullName(currentUserEmail);
        item.setAuthor(author);

        ProductionQueueItem savedItem = productionQueueItemRepository.save(item);

        if (files != null && !files.isEmpty()) {
            List<ProductionFileInfo> fileInfos = new ArrayList<>();
            Map<String, Integer> orderMap = parseFileOrderMapping(fileOrderMapping);

            logger.info("Attachment order before saving in save method for partName: {}", sanitizedPartName);
            for (MultipartFile file : files) {
                String originalFileName = file.getOriginalFilename();
                String sanitizedFileName = fileSystemService.sanitizeName(originalFileName, "UNKNOWN");
                Integer fileOrder = orderMap.getOrDefault(sanitizedFileName, fileInfos.size());
                logger.info("File: {}, order: {}", sanitizedFileName, fileOrder);
            }

            String basePath = getBaseFilePath(savedItem);
            Files.createDirectories(Paths.get(basePath));

            for (MultipartFile file : files) {
                String originalFileName = file.getOriginalFilename();
                String sanitizedFileName = fileSystemService.sanitizeName(originalFileName, "UNKNOWN");
                Integer fileOrder = orderMap.getOrDefault(sanitizedFileName, fileInfos.size());

                Path filePath = Paths.get(basePath, sanitizedFileName);
                Files.write(filePath, file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                logger.debug("Saved file to: {}", filePath);

                ProductionFileInfo fileInfo = ProductionFileInfo.builder()
                        .fileName(sanitizedFileName)
                        .fileType(file.getContentType())
                        .fileSize(file.getSize())
                        .filePath(filePath.toString())
                        .productionQueueItem(savedItem)
                        .completed(false)
                        .order(fileOrder)
                        .build();
                fileInfos.add(fileInfo);
            }

            if (savedItem.getFiles() == null) {
                savedItem.setFiles(new ArrayList<>());
            }

            savedItem.getFiles().addAll(fileInfos);
            productionFileInfoService.saveAll(fileInfos);
        }

        savedItem.setCompleted(checkAllMpfCompleted(savedItem));
        productionQueueItemRepository.save(savedItem);

        syncAttachmentsToMachinePath(savedItem);
        fileWatcherService.checkQueueFile(savedItem.getQueueType());
        machineQueueFileGeneratorService.generateQueueFileForMachine(savedItem.getQueueType());

        // --- POCZĄTEK POPRAWKI ---
        toolListGeneratorService.generateAndStoreToolList(savedItem)
                .ifPresent(toolListInfo -> {
                    savedItem.getFiles().add(toolListInfo);
                    productionFileInfoService.save(toolListInfo);
                });
        // --- KONIEC POPRAWKI ---

        return savedItem;
    }

    @Transactional
    public ProductionQueueItem update(Integer id, ProductionQueueItem updatedItem, List<MultipartFile> files, String fileOrderMapping) throws IOException {
        validateQueueType(updatedItem.getQueueType());
        Optional<ProductionQueueItem> existingItemOpt = productionQueueItemRepository.findByIdWithFiles(id);
        if (existingItemOpt.isPresent()) {
            ProductionQueueItem existingItem = existingItemOpt.get();
            String oldQueueType = existingItem.getQueueType();

            existingItem.setType(updatedItem.getType());
            existingItem.setSubtype(updatedItem.getSubtype());
            existingItem.setOrderName(updatedItem.getOrderName());
            existingItem.setPartName(fileSystemService.sanitizeName(updatedItem.getPartName(), "NoPartName_" + id));
            existingItem.setQuantity(updatedItem.getQuantity());
            existingItem.setBaseCamTime(updatedItem.getBaseCamTime());
            existingItem.setCamTime(updatedItem.getCamTime());
            existingItem.setDeadline(updatedItem.getDeadline());
            existingItem.setSelectedDays(updatedItem.getSelectedDays());
            existingItem.setAdditionalInfo(updatedItem.getAdditionalInfo());
            existingItem.setFileDirectory(updatedItem.getFileDirectory());
            existingItem.setQueueType(updatedItem.getQueueType());
            existingItem.setMaterial(updatedItem.getMaterial());
            existingItem.setMaterialValue(updatedItem.getMaterialValue());
            existingItem.setMaterialProfile(updatedItem.getMaterialProfile());
            existingItem.setMaterialType(updatedItem.getMaterialType());
            existingItem.setMaterialPricePerKg(updatedItem.getMaterialPricePerKg());
            existingItem.setX(updatedItem.getX());
            existingItem.setY(updatedItem.getY());
            existingItem.setZ(updatedItem.getZ());
            existingItem.setDiameter(updatedItem.getDiameter());
            existingItem.setInnerDiameter(updatedItem.getInnerDiameter());
            existingItem.setLength(updatedItem.getLength());

            if (updatedItem.getOrder() != null) {
                existingItem.setOrder(updatedItem.getOrder());
            }

            if (files != null && !files.isEmpty()) {
                Map<String, Integer> orderMapForNewFiles = parseFileOrderMapping(fileOrderMapping);
                String basePath = getBaseFilePath(existingItem);
                Files.createDirectories(Paths.get(basePath));

                Integer maxOrder = existingItem.getFiles().stream()
                        .map(ProductionFileInfo::getOrder)
                        .filter(Objects::nonNull)
                        .max(Integer::compare)
                        .orElse(-1);

                for (MultipartFile file : files) {
                    maxOrder++;
                    String originalFileName = file.getOriginalFilename();
                    String sanitizedFileName = fileSystemService.sanitizeName(originalFileName, "UNKNOWN");

                    Path filePath = Paths.get(basePath, sanitizedFileName);
                    Files.write(filePath, file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                    ProductionFileInfo fileInfo = ProductionFileInfo.builder()
                            .fileName(sanitizedFileName)
                            .fileType(file.getContentType())
                            .fileSize(file.getSize())
                            .filePath(filePath.toString())
                            .productionQueueItem(existingItem)
                            .completed(false)
                            .order(maxOrder)
                            .build();
                    existingItem.getFiles().add(fileInfo);
                }
            }

            Map<String, Integer> orderMap = parseFileOrderMapping(fileOrderMapping);
            if (orderMap != null && !orderMap.isEmpty()) {
                List<ProductionFileInfo> filesToSort = new ArrayList<>(existingItem.getFiles());
                filesToSort.sort(Comparator.comparing(file -> orderMap.getOrDefault(file.getFileName(), Integer.MAX_VALUE)));

                existingItem.getFiles().clear();

                for (int i = 0; i < filesToSort.size(); i++) {
                    ProductionFileInfo file = filesToSort.get(i);
                    file.setOrder(i);
                    existingItem.getFiles().add(file);
                }
            }

            existingItem.setCompleted(updatedItem.isCompleted());
            if (existingItem.getFiles() != null) {
                boolean allMpfCompleted = existingItem.getFiles().stream()
                        .filter(f -> f.getFileName().toLowerCase().endsWith(".mpf"))
                        .allMatch(ProductionFileInfo::isCompleted);
                existingItem.setCompleted(allMpfCompleted);
            }

            ProductionQueueItem savedItem = productionQueueItemRepository.save(existingItem);
            syncAttachmentsToMachinePath(savedItem);

            Set<String> queueTypesToUpdate = new HashSet<>();
            queueTypesToUpdate.add(savedItem.getQueueType());
            if (!oldQueueType.equals(savedItem.getQueueType())) {
                queueTypesToUpdate.add(oldQueueType);
            }

            for (String queueType : queueTypesToUpdate) {
                fileWatcherService.checkQueueFile(queueType);
                machineQueueFileGeneratorService.generateQueueFileForMachine(queueType);
            }

            // --- POCZĄTEK POPRAWKI ---
            toolListGeneratorService.generateAndStoreToolList(savedItem)
                    .ifPresent(toolListInfo -> {
                        savedItem.getFiles().add(toolListInfo);
                        productionFileInfoService.save(toolListInfo);
                    });
            // --- KONIEC POPRAWKI ---

            return savedItem;
        } else {
            throw new RuntimeException("Queue item with ID: " + id + " not found");
        }
    }

    public byte[] getFileContent(Long fileId) throws IOException {
        Optional<ProductionFileInfo> fileOpt = productionFileInfoService.findById(fileId);
        if (fileOpt.isPresent()) {
            ProductionFileInfo file = fileOpt.get();
            Path filePath = Paths.get(file.getFilePath());
            if (Files.exists(filePath)) {
                return Files.readAllBytes(filePath);
            } else {
                throw new IOException("File not found: " + file.getFilePath());
            }
        } else {
            throw new IOException("File info with ID: " + fileId + " not found");
        }
    }

    private String getBaseFilePath(ProductionQueueItem item) {
        String sanitizedOrderName = fileSystemService.sanitizeName(item.getOrderName(), "NoOrderName_" + item.getId());
        String sanitizedPartName = fileSystemService.sanitizeName(item.getPartName(), "NoPartName_" + item.getId());
        return Paths.get(uploadDir, String.valueOf(item.getId()), sanitizedOrderName, sanitizedPartName).toString();
    }

    private String getUserFullName(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String firstName = user.getFirstName() != null && !user.getFirstName().isEmpty() ? user.getFirstName() : "";
            String lastName = user.getLastName() != null && !user.getLastName().isEmpty() ? user.getLastName() : "";
            String fullName = (firstName + " " + lastName).trim();
            return fullName.isEmpty() ? email : fullName;
        }
        return email;
    }

    private Map<String, Integer> parseFileOrderMapping(String fileOrderMapping) throws IOException {
        if (fileOrderMapping == null || fileOrderMapping.isEmpty()) {
            return new HashMap<>();
        }
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> mappingList = mapper.readValue(fileOrderMapping, new TypeReference<>() {});
        Map<String, Integer> orderMap = new HashMap<>();
        for (Map<String, Object> entry : mappingList) {
            String name = (String) entry.get("name");
            Integer order = (Integer) entry.get("order");
            if (name != null && order != null) {
                orderMap.put(name, order);
            }
        }
        return orderMap;
    }

    private String getUniquePartName(String queueType, String partName) {
        String basePartName = partName;
        int suffix = 2;
        String candidatePartName = basePartName;

        while (isPartNameDuplicate(queueType, candidatePartName)) {
            candidatePartName = basePartName + "_" + suffix;
            suffix++;
            if (suffix > 1000) {
                throw new IllegalStateException("Cannot find unique name for partName: " + basePartName);
            }
        }

        return candidatePartName;
    }

    private boolean isPartNameDuplicate(String queueType, String partName) {
        return productionQueueItemRepository.findAll().stream()
                .filter(item -> item.getQueueType().equals(queueType))
                .anyMatch(item -> {
                    String sanitizedExistingPartName = fileSystemService.sanitizeName(item.getPartName(), "NoPartName_" + item.getId());
                    return sanitizedExistingPartName.equalsIgnoreCase(partName);
                });
    }

    public Optional<ProductionQueueItem> findById(Integer id) {
        Optional<ProductionQueueItem> itemOpt = productionQueueItemRepository.findByIdWithFiles(id);
        itemOpt.ifPresent(item -> {
            logger.info("Retrieved queue item ID: {}. Attachment order:", id);
            item.getFiles().forEach(file ->
                    logger.info("File: {}, order: {}, id: {}", file.getFileName(), file.getOrder(), file.getId())
            );
        });
        return itemOpt;
    }

    public List<ProductionQueueItem> findAll() {
        return productionQueueItemRepository.findAll();
    }

    public Page<ProductionQueueItem> findByQueueType(String queueType, Pageable pageable) {
        logger.info("Fetching queue for queueType: {} with pagination: {}", queueType, pageable);
        if (queueType == null) {
            logger.warn("queueType is null, returning empty page");
            return Page.empty();
        }
        Page<ProductionQueueItem> items = productionQueueItemRepository.findByQueueType(queueType, pageable);

        if (pageable.isPaged()) {
            logger.debug("Found {} items on page {} for queueType: {}", items.getNumberOfElements(), pageable.getPageNumber(), queueType);
        } else {
            logger.debug("Found {} items (unpaged) for queueType: {}", items.getTotalElements(), queueType);
        }
        return items;
    }

    @Transactional
    public void deleteById(Integer id) throws IOException {
        Optional<ProductionQueueItem> itemOpt = productionQueueItemRepository.findByIdWithFiles(id);
        if (itemOpt.isPresent()) {
            ProductionQueueItem item = itemOpt.get();
            String queueType = item.getQueueType();

            for (ProductionFileInfo file : item.getFiles()) {
                if (file.getFilePath() != null) {
                    try {
                        Path filePath = Paths.get(file.getFilePath());
                        if (Files.exists(filePath)) {
                            Files.delete(filePath);
                            logger.info("Deleted file from disk: {}", filePath);
                        }
                    } catch (IOException e) {
                        logger.error("Failed to delete file from disk: {}. Error: {}", file.getFilePath(), e.getMessage());
                    }
                }

                if (queueType != null && !"ncQueue".equals(queueType) && !"completed".equals(queueType)) {
                    Optional<Machine> machineOpt = machineRepository.findById(Integer.parseInt(queueType));
                    if (machineOpt.isPresent()) {
                        Machine machine = machineOpt.get();
                        String programPath = machine.getProgramPath();
                        String orderName = fileSystemService.sanitizeName(item.getOrderName(), "NoOrderName_" + item.getId());
                        String partName = fileSystemService.sanitizeName(item.getPartName(), "NoPartName_" + item.getId());
                        String fileName = fileSystemService.sanitizeName(file.getFileName(), "UNKNOWN");

                        Path machineFilePath = Paths.get(programPath, orderName, partName, fileName);
                        try {
                            if (Files.exists(machineFilePath)) {
                                Files.delete(machineFilePath);
                                logger.info("Deleted file from machine disk: {}", machineFilePath);
                            }
                        } catch (IOException e) {
                            logger.error("Failed to delete file from machine disk: {}. Error: {}", machineFilePath, e.getMessage());
                        }
                    }
                }
            }

            productionQueueItemRepository.deleteById(id);
            logger.info("Deleted ProductionQueueItem with ID: {}", id);

            fileWatcherService.checkQueueFile(queueType);
            machineQueueFileGeneratorService.generateQueueFileForMachine(queueType);
        } else {
            throw new RuntimeException("ProductionQueueItem with ID: " + id + " not found");
        }
    }

    @Transactional
    public void syncWithMachine(String queueType) throws IOException {
        logger.info("Starting synchronization with machine for queueType: {}", queueType);

        if (queueType == null || "ncQueue".equals(queueType) || "completed".equals(queueType)) {
            logger.debug("Skipping synchronization for queueType: {}", queueType);
            return;
        }

        Optional<Machine> machineOpt;
        try {
            machineOpt = machineRepository.findById(Integer.parseInt(queueType));
        } catch (NumberFormatException e) {
            logger.warn("Invalid queueType format: {}", queueType);
            throw new IllegalArgumentException("Invalid queueType: " + queueType);
        }

        if (machineOpt.isEmpty()) {
            logger.warn("Machine not found for queueType: {}", queueType);
            throw new IllegalArgumentException("Machine not found for queueType: " + queueType);
        }

        Machine machine = machineOpt.get();
        fileWatcherService.checkQueueFile(queueType);

        List<ProductionQueueItem> programs = productionQueueItemRepository.findByQueueType(queueType, Pageable.unpaged()).getContent();
        for (ProductionQueueItem program : programs) {
            String orderName = fileSystemService.sanitizeName(program.getOrderName(), "NoOrderName_" + program.getId());
            String partName = fileSystemService.sanitizeName(program.getPartName(), "NoPartName_" + program.getId());
            try {
                fileSystemService.synchronizeFiles(machine.getProgramPath(), orderName, partName, program.getFiles());
                logger.debug("Synchronized attachments for program ID: {}, orderName: {}, partName: {}",
                        program.getId(), orderName, partName);
            } catch (IOException e) {
                logger.error("Failed to synchronize attachments for program ID: {}, machine: {}: {}",
                        program.getId(), machine.getMachineName(), e.getMessage());
                throw new FileOperationException("Failed to synchronize attachments for program ID: " + program.getId(), e);
            }
        }

        machineQueueFileGeneratorService.generateQueueFileForMachine(queueType);
        logger.info("Completed synchronization with machine for queueType: {}", queueType);
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
            machineQueueFileGeneratorService.generateQueueFileForMachine(savedItem.getQueueType());
            return savedItem;
        } else {
            throw new RuntimeException("Queue item with ID: " + id + " not found");
        }
    }

    @Transactional
    public void updateQueueOrder(String queueType, List<OrderItem> items) throws IOException {
        validateQueueType(queueType);
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
        }

        for (String qt : queueTypesToUpdate) {
            logger.info("Synchronizing queue for queueType: {}", qt);
            fileWatcherService.checkQueueFile(qt);
            machineQueueFileGeneratorService.generateQueueFileForMachine(qt);
        }
    }

    @Transactional
    public List<ProductionQueueItem> moveCompletedPrograms(Integer machineId) throws IOException {
        logger.info("Moving completed programs for machine ID: {}", machineId);
        String queueType = String.valueOf(machineId);
        List<ProductionQueueItem> items = productionQueueItemRepository.findByQueueType(queueType, Pageable.unpaged()).getContent();
        List<ProductionQueueItem> itemsToMove = items.stream()
                .filter(ProductionQueueItem::isCompleted)
                .collect(Collectors.toList());

        if (itemsToMove.isEmpty()) {
            logger.info("No completed programs to move for machine ID: {}", machineId);
            return Collections.emptyList();
        }

        List<ProductionQueueItem> existingCompletedQueue = productionQueueItemRepository
                .findByQueueType("completed", Pageable.unpaged()).getContent();

        List<ProductionQueueItem> itemsToSave = new ArrayList<>();

        int shiftAmount = itemsToMove.size();
        for (ProductionQueueItem existingItem : existingCompletedQueue) {
            existingItem.setOrder(existingItem.getOrder() + shiftAmount);
            itemsToSave.add(existingItem);
        }

        int newOrder = 0;
        for (ProductionQueueItem item : itemsToMove) {
            String oldQueueType = item.getQueueType();
            item.setQueueType("completed");
            item.setOrder(newOrder++);
            itemsToSave.add(item);
            logger.info("Moving program ID: {} from queueType: {} to completed with new order: {}", item.getId(), oldQueueType, item.getOrder());
        }

        productionQueueItemRepository.saveAll(itemsToSave);

        Set<String> queueTypesToUpdate = new HashSet<>();
        queueTypesToUpdate.add(queueType);
        queueTypesToUpdate.add("completed");

        for (String qt : queueTypesToUpdate) {
            logger.info("Synchronizing queue for queueType: {}", qt);
            fileWatcherService.checkQueueFile(qt);
            machineQueueFileGeneratorService.generateQueueFileForMachine(qt);
        }

        return itemsToMove;
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

    private void syncAttachmentsToMachinePath(ProductionQueueItem item) throws IOException {
        try {
            String queueType = item.getQueueType();
            if (queueType == null || "ncQueue".equals(queueType) || "completed".equals(queueType)) {
                return;
            }

            Optional<Machine> machineOpt = machineRepository.findById(Integer.parseInt(queueType));
            if (machineOpt.isEmpty()) {
                throw new FileOperationException("Machine with ID: " + queueType + " not found");
            }

            Machine machine = machineOpt.get();
            String programPath = machine.getProgramPath();

            String orderName = fileSystemService.sanitizeName(item.getOrderName(), "NoOrderName_" + item.getId());
            String partName = fileSystemService.sanitizeName(item.getPartName(), "NoPartName_" + item.getId());

            fileSystemService.synchronizeFiles(programPath, orderName, partName, item.getFiles());
        } catch (IOException e) {
            throw new FileOperationException("Failed to synchronize attachments with machine directory: " + e.getMessage(), e);
        }
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