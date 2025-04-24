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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for managing production queue items, including saving, updating, and synchronizing attachments.
 */
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

    @Autowired
    public ProductionQueueItemService(
            ProductionQueueItemRepository productionQueueItemRepository,
            ProductionFileInfoService productionFileInfoService,
            MachineRepository machineRepository,
            MachineQueueFileGeneratorService machineQueueFileGeneratorService,
            FileWatcherService fileWatcherService,
            ProductionQueueItemRepository productionQueueItemRepositoryForFileSystem,
            UserRepository userRepository) {
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.productionFileInfoService = productionFileInfoService;
        this.machineRepository = machineRepository;
        this.machineQueueFileGeneratorService = machineQueueFileGeneratorService;
        this.fileWatcherService = fileWatcherService;
        this.fileSystemService = new FileSystemService(productionQueueItemRepositoryForFileSystem);
        this.userRepository = userRepository;
    }

    /**
     * Saves a new production queue item along with its attachments and considers fileOrderMapping.
     */
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

        // Sanitize partName and ensure it's unique
        String sanitizedPartName = fileSystemService.sanitizeName(item.getPartName(), "NoPartName_" + System.currentTimeMillis());
        sanitizedPartName = getUniquePartName(item.getQueueType(), sanitizedPartName);
        item.setPartName(sanitizedPartName);

        // Set author as firstName + lastName
        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        String author = getUserFullName(currentUserEmail);
        item.setAuthor(author);

        ProductionQueueItem savedItem = productionQueueItemRepository.save(item);

        if (files != null && !files.isEmpty()) {
            List<ProductionFileInfo> fileInfos = new ArrayList<>();
            Map<String, Integer> orderMap = parseFileOrderMapping(fileOrderMapping);

            // Log attachment order before saving
            logger.info("Attachment order before saving in save method for partName: {}", sanitizedPartName);
            for (MultipartFile file : files) {
                String originalFileName = file.getOriginalFilename();
                String sanitizedFileName = fileSystemService.sanitizeName(originalFileName, "UNKNOWN", originalFileName != null && originalFileName.toLowerCase().endsWith(".mpf"));
                Integer fileOrder = orderMap.getOrDefault(sanitizedFileName, fileInfos.size());
                logger.info("File: {}, order: {}", sanitizedFileName, fileOrder);
            }

            for (MultipartFile file : files) {
                String originalFileName = file.getOriginalFilename();
                String sanitizedFileName = fileSystemService.sanitizeName(originalFileName, "UNKNOWN", originalFileName != null && originalFileName.toLowerCase().endsWith(".mpf"));
                Integer fileOrder = orderMap.getOrDefault(sanitizedFileName, fileInfos.size());

                ProductionFileInfo fileInfo = ProductionFileInfo.builder()
                        .fileName(sanitizedFileName)
                        .fileType(file.getContentType())
                        .fileSize(file.getSize())
                        .fileContent(file.getBytes())
                        .productionQueueItem(savedItem)
                        .completed(false)
                        .order(fileOrder)
                        .build();
                fileInfos.add(fileInfo);
            }
            savedItem.setFiles(fileInfos);
            productionFileInfoService.saveAll(fileInfos);
        }

        savedItem.setCompleted(checkAllMpfCompleted(savedItem));
        productionQueueItemRepository.save(savedItem);

        syncAttachmentsToMachinePath(savedItem);
        fileWatcherService.checkQueueFile(savedItem.getQueueType());
        machineQueueFileGeneratorService.generateQueueFileForMachine(savedItem.getQueueType());

        return savedItem;
    }

    /**
     * Updates an existing production queue item and its attachments, considering fileOrderMapping.
     */
    @Transactional
    public ProductionQueueItem update(Integer id, ProductionQueueItem updatedItem, List<MultipartFile> files, String fileOrderMapping) throws IOException {
        validateQueueType(updatedItem.getQueueType());
        Optional<ProductionQueueItem> existingItemOpt = productionQueueItemRepository.findById(id);
        if (existingItemOpt.isPresent()) {
            ProductionQueueItem existingItem = existingItemOpt.get();
            String oldQueueType = existingItem.getQueueType();

            // Update fields
            existingItem.setType(updatedItem.getType());
            existingItem.setSubtype(updatedItem.getSubtype());
            existingItem.setOrderName(updatedItem.getOrderName());
            existingItem.setPartName(fileSystemService.sanitizeName(updatedItem.getPartName(), "NoPartName_" + id));
            existingItem.setQuantity(updatedItem.getQuantity());
            existingItem.setBaseCamTime(updatedItem.getBaseCamTime());
            existingItem.setCamTime(updatedItem.getCamTime());
            existingItem.setDeadline(updatedItem.getDeadline());
            existingItem.setAdditionalInfo(updatedItem.getAdditionalInfo());
            existingItem.setFileDirectory(updatedItem.getFileDirectory());
            existingItem.setQueueType(updatedItem.getQueueType());
            existingItem.setOrder(updatedItem.getOrder());

            // Set author as firstName + lastName
            String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            String author = getUserFullName(currentUserEmail);
            existingItem.setAuthor(author);

            // Log existing attachments
            logger.info("Order of existing attachments before update for ID: {}", id);
            for (ProductionFileInfo file : existingItem.getFiles()) {
                logger.info("File: {}, order: {}, ID: {}", file.getFileName(), file.getOrder(), file.getId());
            }

            // Handle new attachments
            Map<String, Integer> orderMap = parseFileOrderMapping(fileOrderMapping);
            if (files != null && !files.isEmpty()) {
                List<ProductionFileInfo> fileInfos = new ArrayList<>();
                // Log new attachments
                logger.info("Order of new attachments before saving for ID: {}", id);
                for (MultipartFile file : files) {
                    String originalFileName = file.getOriginalFilename();
                    String sanitizedFileName = fileSystemService.sanitizeName(originalFileName, "UNKNOWN", originalFileName != null && originalFileName.toLowerCase().endsWith(".mpf"));
                    Integer fileOrder = orderMap.getOrDefault(sanitizedFileName, fileInfos.size());
                    logger.info("File: {}, order: {}", sanitizedFileName, fileOrder);
                }

                for (MultipartFile file : files) {
                    String originalFileName = file.getOriginalFilename();
                    String sanitizedFileName = fileSystemService.sanitizeName(originalFileName, "UNKNOWN", originalFileName != null && originalFileName.toLowerCase().endsWith(".mpf"));
                    Integer fileOrder = orderMap.getOrDefault(sanitizedFileName, fileInfos.size());

                    ProductionFileInfo fileInfo = ProductionFileInfo.builder()
                            .fileName(sanitizedFileName)
                            .fileType(file.getContentType())
                            .fileSize(file.getSize())
                            .fileContent(file.getBytes())
                            .productionQueueItem(existingItem)
                            .completed(false)
                            .order(fileOrder)
                            .build();
                    fileInfos.add(fileInfo);
                }
                existingItem.getFiles().addAll(fileInfos);
                productionFileInfoService.saveAll(fileInfos);
            }

            // Update order of existing files
            if (orderMap != null && !orderMap.isEmpty()) {
                for (ProductionFileInfo file : existingItem.getFiles()) {
                    Integer newOrder = orderMap.get(file.getFileName());
                    if (newOrder != null) {
                        file.setOrder(newOrder);
                    }
                }
            }

            // Log after updating order
            logger.info("Order of attachments after update for ID: {}", id);
            for (ProductionFileInfo file : existingItem.getFiles()) {
                logger.info("File: {}, order: {}, ID: {}", file.getFileName(), file.getOrder(), file.getId());
            }

            existingItem.setCompleted(checkAllMpfCompleted(existingItem));
            ProductionQueueItem savedItem = productionQueueItemRepository.save(existingItem);
            // Synchronize attachments
            syncAttachmentsToMachinePath(savedItem);

            // Update queue files for new and old queueType
            Set<String> queueTypesToUpdate = new HashSet<>();
            queueTypesToUpdate.add(savedItem.getQueueType());
            if (!oldQueueType.equals(savedItem.getQueueType())) {
                logger.info("Added old queueType for update: {}", oldQueueType);
                queueTypesToUpdate.add(oldQueueType);
            }

            for (String queueType : queueTypesToUpdate) {
                logger.info("Synchronizing queue for queueType: {}", queueType);
                fileWatcherService.checkQueueFile(queueType);
                machineQueueFileGeneratorService.generateQueueFileForMachine(queueType);
            }

            return savedItem;
        } else {
            throw new RuntimeException("Production queue item not found with ID: " + id);
        }
    }

    /**
     * Retrieves the full name (firstName + lastName) of the user based on their email.
     * If the user is not found or names are empty, returns the email as fallback.
     */
    private String getUserFullName(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String firstName = user.getFirstName() != null && !user.getFirstName().isEmpty() ? user.getFirstName() : "";
            String lastName = user.getLastName() != null && !user.getLastName().isEmpty() ? user.getLastName() : "";
            String fullName = (firstName + " " + lastName).trim();
            return fullName.isEmpty() ? email : fullName;
        }
        return email; // Fallback to email if user not found
    }

    /**
     * Parses fileOrderMapping from JSON to a map of file name -> order.
     */
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

    /**
     * Generates a unique partName by adding a suffix _2, _3, etc., if the name already exists in the queueType.
     */
    private String getUniquePartName(String queueType, String partName) {
        String basePartName = partName;
        int suffix = 2;
        String candidatePartName = basePartName;

        while (isPartNameDuplicate(queueType, candidatePartName)) {
            candidatePartName = basePartName + "_" + suffix;
            suffix++;
            if (suffix > 1000) {
                throw new IllegalStateException("Cannot find a unique name for partName: " + basePartName);
            }
        }

        return candidatePartName;
    }

    /**
     * Checks if partName already exists in the given queueType.
     */
    private boolean isPartNameDuplicate(String queueType, String partName) {
        return productionQueueItemRepository.findByQueueType(queueType)
                .stream()
                .anyMatch(item -> {
                    String sanitizedExistingPartName = fileSystemService.sanitizeName(item.getPartName(), "NoPartName_" + item.getId());
                    return sanitizedExistingPartName.equalsIgnoreCase(partName);
                });
    }

    /**
     * Finds a production queue item by ID.
     */
    public Optional<ProductionQueueItem> findById(Integer id) {
        Optional<ProductionQueueItem> itemOpt = productionQueueItemRepository.findByIdWithFiles(id);
        itemOpt.ifPresent(item -> {
            logger.info("Retrieved production queue item ID: {}. Attachment order:", id);
            item.getFiles().forEach(file ->
                    logger.info("File: {}, order: {}, id: {}", file.getFileName(), file.getOrder(), file.getId())
            );
        });
        return itemOpt;
    }

    /**
     * Returns all production queue items.
     */
    public List<ProductionQueueItem> findAll() {
        return productionQueueItemRepository.findAll();
    }

    /**
     * Deletes a production queue item by ID.
     */
    @Transactional
    public void deleteById(Integer id) throws IOException {
        Optional<ProductionQueueItem> itemOpt = productionQueueItemRepository.findById(id);
        if (itemOpt.isPresent()) {
            ProductionQueueItem item = itemOpt.get();
            String queueType = item.getQueueType();
            productionQueueItemRepository.deleteById(id);
            fileWatcherService.checkQueueFile(queueType);
            machineQueueFileGeneratorService.generateQueueFileForMachine(queueType);
        }
    }

    /**
     * Finds production queue items by queue type.
     */
    public List<ProductionQueueItem> findByQueueType(String queueType) {
        logger.info("Fetching queue for queueType: {}", queueType);
        if (queueType == null) {
            logger.warn("queueType is null, returning empty list");
            return Collections.emptyList();
        }
        List<ProductionQueueItem> items = productionQueueItemRepository.findByQueueType(queueType);
        logger.debug("Found {} items for queueType: {}", items.size(), queueType);
        return items;
    }

    /**
     * Synchronizes production queue statuses with the machine queue file.
     */
    @Transactional
    public void syncWithMachine(String queueType) throws IOException {
        logger.info("Starting synchronization with machine for queueType: {}", queueType);
        fileWatcherService.checkQueueFile(queueType);
        logger.info("Completed synchronization with machine for queueType: {}", queueType);
    }

    /**
     * Toggles the completion status of a production queue item.
     */
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
            throw new RuntimeException("Production queue item not found with ID: " + id);
        }
    }

    /**
     * Updates the order of items in the production queue.
     */
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

    /**
     * Checks if all .MPF attachments for an item are completed.
     */
    private boolean checkAllMpfCompleted(ProductionQueueItem item) {
        if (item.getFiles() == null || item.getFiles().isEmpty()) {
            return false;
        }
        return item.getFiles().stream()
                .filter(f -> f.getFileName().toLowerCase().endsWith(".mpf"))
                .allMatch(ProductionFileInfo::isCompleted);
    }

    /**
     * Validates the queue type.
     */
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
                throw new IllegalArgumentException("Invalid queue type: " + queueType);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid queue type: " + queueType);
        }
    }

    /**
     * Synchronizes item attachments with the machine directory.
     */
    private void syncAttachmentsToMachinePath(ProductionQueueItem item) {
        try {
            String queueType = item.getQueueType();
            if (queueType == null || "ncQueue".equals(queueType) || "completed".equals(queueType)) {
                return;
            }

            Optional<Machine> machineOpt = machineRepository.findById(Integer.parseInt(queueType));
            if (machineOpt.isEmpty()) {
                throw new FileOperationException("Machine not found with ID: " + queueType);
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

    /**
     * Exception thrown for file operation errors.
     */
    private static class FileOperationException extends RuntimeException {
        public FileOperationException(String message) {
            super(message);
        }

        public FileOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}