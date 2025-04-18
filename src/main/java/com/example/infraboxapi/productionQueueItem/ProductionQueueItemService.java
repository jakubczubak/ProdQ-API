package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
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
 * Serwis odpowiedzialny za zarządzanie elementami kolejki produkcyjnej, w tym zapisywanie, aktualizowanie i synchronizację załączników.
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

    @Autowired
    public ProductionQueueItemService(
            ProductionQueueItemRepository productionQueueItemRepository,
            ProductionFileInfoService productionFileInfoService,
            MachineRepository machineRepository,
            MachineQueueFileGeneratorService machineQueueFileGeneratorService,
            FileWatcherService fileWatcherService,
            ProductionQueueItemRepository productionQueueItemRepositoryForFileSystem) {
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.productionFileInfoService = productionFileInfoService;
        this.machineRepository = machineRepository;
        this.machineQueueFileGeneratorService = machineQueueFileGeneratorService;
        this.fileWatcherService = fileWatcherService;
        this.fileSystemService = new FileSystemService(productionQueueItemRepositoryForFileSystem);
    }

    /**
     * Zapisuje nowy element kolejki produkcyjnej wraz z załącznikami i uwzględnia fileOrderMapping.
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

        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        item.setAuthor(currentUserEmail);

        ProductionQueueItem savedItem = productionQueueItemRepository.save(item);

        if (files != null && !files.isEmpty()) {
            List<ProductionFileInfo> fileInfos = new ArrayList<>();
            Map<String, Integer> orderMap = parseFileOrderMapping(fileOrderMapping);

            // Logowanie kolejności załączników przed zapisaniem
            logger.info("Kolejność załączników przed zapisaniem w metodzie save dla partName: {}", sanitizedPartName);
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
     * Aktualizuje istniejący element kolejki produkcyjnej oraz jego załączniki z uwzględnieniem fileOrderMapping.
     */
    @Transactional
    public ProductionQueueItem update(Integer id, ProductionQueueItem updatedItem, List<MultipartFile> files, String fileOrderMapping) throws IOException {
        validateQueueType(updatedItem.getQueueType());
        Optional<ProductionQueueItem> existingItemOpt = productionQueueItemRepository.findById(id);
        if (existingItemOpt.isPresent()) {
            ProductionQueueItem existingItem = existingItemOpt.get();
            String oldQueueType = existingItem.getQueueType();

            // Aktualizacja pól
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

            String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            existingItem.setAuthor(currentUserEmail);

            // Logowanie istniejących załączników
            logger.info("Kolejność istniejących załączników przed aktualizacją dla ID: {}", id);
            for (ProductionFileInfo file : existingItem.getFiles()) {
                logger.info("Plik: {}, kolejność: {}, ID: {}", file.getFileName(), file.getOrder(), file.getId());
            }

            // Obsługa nowych załączników
            Map<String, Integer> orderMap = parseFileOrderMapping(fileOrderMapping);
            if (files != null && !files.isEmpty()) {
                List<ProductionFileInfo> fileInfos = new ArrayList<>();
                // Logowanie nowych załączników
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

            // Aktualizacja kolejności istniejących plików
            if (orderMap != null && !orderMap.isEmpty()) {
                for (ProductionFileInfo file : existingItem.getFiles()) {
                    Integer newOrder = orderMap.get(file.getFileName());
                    if (newOrder != null) {
                        file.setOrder(newOrder);
                    }
                }
            }

            // Logowanie po aktualizacji kolejności
            logger.info("Kolejność załączników po aktualizacji dla ID: {}", id);
            for (ProductionFileInfo file : existingItem.getFiles()) {
                logger.info("Plik: {}, kolejność: {}, ID: {}", file.getFileName(), file.getOrder(), file.getId());
            }

            existingItem.setCompleted(checkAllMpfCompleted(existingItem));
            ProductionQueueItem savedItem = productionQueueItemRepository.save(existingItem);
            // Synchronizacja załączników
            syncAttachmentsToMachinePath(savedItem);

            // Aktualizacja plików kolejki dla nowego i starego queueType
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
    /**
     * Parsuje fileOrderMapping z JSON na mapę nazwa pliku -> kolejność.
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
     * Generuje unikalną nazwę partName, dodając suffix _2, _3, itp., jeśli nazwa już istnieje w danej queueType.
     */
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

    /**
     * Sprawdza, czy partName już istnieje w danej queueType.
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
     * Wyszukuje element kolejki po identyfikatorze.
     */
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

    /**
     * Zwraca wszystkie elementy kolejki produkcyjnej.
     */
    public List<ProductionQueueItem> findAll() {
        return productionQueueItemRepository.findAll();
    }

    /**
     * Usuwa element kolejki produkcyjnej o podanym identyfikatorze.
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
     * Wyszukuje elementy kolejki produkcyjnej według typu kolejki.
     */
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

    /**
     * Synchronizuje statusy kolejki produkcyjnej z plikiem kolejki maszyny.
     */
    @Transactional
    public void syncWithMachine(String queueType) throws IOException {
        logger.info("Rozpoczęcie synchronizacji z maszyną dla queueType: {}", queueType);
        fileWatcherService.checkQueueFile(queueType);
        logger.info("Zakończono synchronizację z maszyną dla queueType: {}", queueType);
    }

    /**
     * Przełącza status ukończenia elementu kolejki produkcyjnej.
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
            throw new RuntimeException("Nie znaleziono elementu kolejki o ID: " + id);
        }
    }

    /**
     * Aktualizuje kolejność elementów w kolejce produkcyjnej.
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

    /**
     * Sprawdza, czy wszystkie załączniki .MPF dla elementu są ukończone.
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
     * Weryfikuje poprawność typu kolejki.
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
                throw new IllegalArgumentException("Nieprawidłowy typ kolejki: " + queueType);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Nieprawidłowy typ kolejki: " + queueType);
        }
    }

    /**
     * Synchronizuje załączniki elementu z katalogiem maszyny.
     */
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

    /**
     * Wyjątek rzucany w przypadku błędów operacji na plikach.
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