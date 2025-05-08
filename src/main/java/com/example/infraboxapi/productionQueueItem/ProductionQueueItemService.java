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

    @Transactional
    public ProductionQueueItem update(Integer id, ProductionQueueItem updatedItem, List<MultipartFile> files, String fileOrderMapping) throws IOException {
        validateQueueType(updatedItem.getQueueType());
        Optional<ProductionQueueItem> existingItemOpt = productionQueueItemRepository.findById(id);
        if (existingItemOpt.isPresent()) {
            ProductionQueueItem existingItem = existingItemOpt.get();
            String oldQueueType = existingItem.getQueueType();

            logger.info("Aktualizacja elementu kolejki ID: {}, obecna kolejność: {}, queueType: {}", id, existingItem.getOrder(), existingItem.getQueueType());
            logger.info("Nowa wartość order z updatedItem: {}", updatedItem.getOrder());

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

            if (updatedItem.getOrder() != null) {
                existingItem.setOrder(updatedItem.getOrder());
            } else {
                logger.debug("updatedItem.getOrder() jest null, zachowuję oryginalną wartość order: {}", existingItem.getOrder());
            }

            String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            String author = getUserFullName(currentUserEmail);
            existingItem.setAuthor(author);

            logger.info("Kolejność istniejących załączników przed aktualizacją dla ID: {}", id);
            for (ProductionFileInfo file : existingItem.getFiles()) {
                logger.info("Plik: {}, kolejność: {}, ID: {}", file.getFileName(), file.getOrder(), file.getId());
            }

            Map<String, Integer> orderMap = parseFileOrderMapping(fileOrderMapping);
            if (files != null && !files.isEmpty()) {
                List<ProductionFileInfo> fileInfos = new ArrayList<>();
                logger.info("Kolejność nowych załączników przed zapisaniem dla ID: {}", id);
                for (MultipartFile file : files) {
                    String originalFileName = file.getOriginalFilename();
                    String sanitizedFileName = fileSystemService.sanitizeName(originalFileName, "UNKNOWN", originalFileName != null && originalFileName.toLowerCase().endsWith(".mpf"));
                    Integer fileOrder = orderMap.getOrDefault(sanitizedFileName, fileInfos.size());
                    logger.info("Plik: {}, kolejność: {}", sanitizedFileName, fileOrder);
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

            if (orderMap != null && !orderMap.isEmpty()) {
                for (ProductionFileInfo file : existingItem.getFiles()) {
                    Integer newOrder = orderMap.get(file.getFileName());
                    if (newOrder != null) {
                        file.setOrder(newOrder);
                    }
                }
            }

            logger.info("Kolejność załączników po aktualizacji dla ID: {}", id);
            for (ProductionFileInfo file : existingItem.getFiles()) {
                logger.info("Plik: {}, kolejność: {}, ID: {}", file.getFileName(), file.getOrder(), file.getId());
            }
            logger.info("Po aktualizacji elementu kolejki ID: {}, kolejność: {}, queueType: {}", id, existingItem.getOrder(), existingItem.getQueueType());

            existingItem.setCompleted(checkAllMpfCompleted(existingItem));
            ProductionQueueItem savedItem = productionQueueItemRepository.save(existingItem);
            syncAttachmentsToMachinePath(savedItem);

            Set<String> queueTypesToUpdate = new HashSet<>();
            queueTypesToUpdate.add(savedItem.getQueueType());
            if (!oldQueueType.equals(savedItem.getQueueType())) {
                logger.info("Dodano stary queueType do aktualizacji: {}", oldQueueType);
                queueTypesToUpdate.add(oldQueueType);
            }

            for (String queueType : queueTypesToUpdate) {
                logger.info("Synchronizacja kolejki dla queueType: {}", queueType);
                fileWatcherService.checkQueueFile(queueType);
                machineQueueFileGeneratorService.generateQueueFileForMachine(queueType);
            }

            return savedItem;
        } else {
            throw new RuntimeException("Nie znaleziono elementu kolejki o ID: " + id);
        }
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
                throw new IllegalStateException("Nie można znaleźć unikalnej nazwy dla partName: " + basePartName);
            }
        }

        return candidatePartName;
    }

    private boolean isPartNameDuplicate(String queueType, String partName) {
        return productionQueueItemRepository.findByQueueType(queueType)
                .stream()
                .anyMatch(item -> {
                    String sanitizedExistingPartName = fileSystemService.sanitizeName(item.getPartName(), "NoPartName_" + item.getId());
                    return sanitizedExistingPartName.equalsIgnoreCase(partName);
                });
    }

    public Optional<ProductionQueueItem> findById(Integer id) {
        Optional<ProductionQueueItem> itemOpt = productionQueueItemRepository.findByIdWithFiles(id);
        itemOpt.ifPresent(item -> {
            logger.info("Pobrano element kolejki ID: {}. Kolejność załączników:", id);
            item.getFiles().forEach(file ->
                    logger.info("Plik: {}, order: {}, id: {}", file.getFileName(), file.getOrder(), file.getId())
            );
        });
        return itemOpt;
    }

    public List<ProductionQueueItem> findAll() {
        return productionQueueItemRepository.findAll();
    }

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

    public List<ProductionQueueItem> findByQueueType(String queueType) {
        logger.info("Pobieranie kolejki dla queueType: {}", queueType);
        if (queueType == null) {
            logger.warn("queueType jest null, zwracam pustą listę");
            return Collections.emptyList();
        }
        List<ProductionQueueItem> items = productionQueueItemRepository.findByQueueType(queueType);
        logger.debug("Znaleziono {} elementów dla queueType: {}", items.size(), queueType);
        return items;
    }

    @Transactional
    public void syncWithMachine(String queueType) throws IOException {
        logger.info("Rozpoczęcie synchronizacji z maszyną dla queueType: {}", queueType);
        fileWatcherService.checkQueueFile(queueType);
        logger.info("Zakończono synchronizację z maszyną dla queueType: {}", queueType);
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
            throw new RuntimeException("Nie znaleziono elementu kolejki o ID: " + id);
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
                throw new IllegalArgumentException("Nie znaleziono elementu: " + orderItem.getId());
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
            logger.info("Synchronizacja kolejki dla queueType: {}", qt);
            fileWatcherService.checkQueueFile(qt);
            machineQueueFileGeneratorService.generateQueueFileForMachine(qt);
        }
    }

    @Transactional
    public List<ProductionQueueItem> moveCompletedPrograms(Integer machineId) throws IOException {
        logger.info("Przenoszenie ukończonych programów dla maszyny ID: {}", machineId);
        String queueType = String.valueOf(machineId);
        List<ProductionQueueItem> items = productionQueueItemRepository.findByQueueType(queueType);
        List<ProductionQueueItem> completedItems = items.stream()
                .filter(ProductionQueueItem::isCompleted)
                .collect(Collectors.toList());

        if (completedItems.isEmpty()) {
            logger.info("Brak ukończonych programów do przeniesienia dla maszyny ID: {}", machineId);
            return Collections.emptyList();
        }

        Integer maxCompletedOrder = productionQueueItemRepository.findMaxOrderByQueueType("completed");
        int nextOrder = maxCompletedOrder != null ? maxCompletedOrder + 1 : 1;

        for (ProductionQueueItem item : completedItems) {
            String oldQueueType = item.getQueueType();
            item.setQueueType("completed");
            item.setOrder(nextOrder++);
            syncAttachmentsToMachinePath(item);
            productionQueueItemRepository.save(item);
            logger.info("Przeniesiono program ID: {} z queueType: {} do completed", item.getId(), oldQueueType);
        }

        Set<String> queueTypesToUpdate = new HashSet<>();
        queueTypesToUpdate.add(queueType);
        queueTypesToUpdate.add("completed");

        for (String qt : queueTypesToUpdate) {
            logger.info("Synchronizacja kolejki dla queueType: {}", qt);
            fileWatcherService.checkQueueFile(qt);
            machineQueueFileGeneratorService.generateQueueFileForMachine(qt);
        }

        return completedItems;
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
                throw new IllegalArgumentException("Nieprawidłowy typ kolejki: " + queueType);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Nieprawidłowy typ kolejki: " + queueType);
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
                throw new FileOperationException("Nie znaleziono maszyny o ID: " + queueType);
            }

            Machine machine = machineOpt.get();
            String programPath = machine.getProgramPath();

            String orderName = fileSystemService.sanitizeName(item.getOrderName(), "NoOrderName_" + item.getId());
            String partName = fileSystemService.sanitizeName(item.getPartName(), "NoPartName_" + item.getId());

            fileSystemService.synchronizeFiles(programPath, orderName, partName, item.getFiles());
        } catch (IOException e) {
            throw new FileOperationException("Nie udało się zsynchronizować załączników z katalogiem maszyny: " + e.getMessage(), e);
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