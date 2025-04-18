package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
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
     * Zapisuje nowy element kolejki produkcyjnej wraz z załącznikami.
     * Jeśli partName już istnieje w danej queueType, dodaje unikalny suffix (np. _2, _3, itp.).
     *
     * @param item element kolejki do zapisania
     * @param files lista załączników do zapisania
     * @return zapisany element kolejki
     * @throws IOException w przypadku błędu operacji na pliku
     */
    @Transactional
    public ProductionQueueItem save(ProductionQueueItem item, List<MultipartFile> files) throws IOException {
        if (item.getQueueType() == null || item.getQueueType().isEmpty()) {
            item.setQueueType("ncQueue");
        }

        if (item.getOrder() == null) {
            Integer maxOrder = productionQueueItemRepository.findMaxOrderByQueueType(item.getQueueType());
            item.setOrder(maxOrder != null ? maxOrder + 1 : 1);
        }

        // Sanitize partName and ensure it's unique
        String sanitizedPartName = sanitizeFileName(item.getPartName(), "NoPartName_" + System.currentTimeMillis());
        sanitizedPartName = getUniquePartName(item.getQueueType(), sanitizedPartName);
        item.setPartName(sanitizedPartName);

        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        item.setAuthor(currentUserEmail);

        ProductionQueueItem savedItem = productionQueueItemRepository.save(item);

        if (files != null && !files.isEmpty()) {
            List<ProductionFileInfo> fileInfos = new ArrayList<>();
            for (MultipartFile file : files) {
                String originalFileName = file.getOriginalFilename();
                String sanitizedFileName = sanitizeFileName(originalFileName, "UNKNOWN", originalFileName.toLowerCase().endsWith(".mpf"));
                ProductionFileInfo fileInfo = ProductionFileInfo.builder()
                        .fileName(sanitizedFileName)
                        .fileType(file.getContentType())
                        .fileSize(file.getSize())
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
        fileWatcherService.checkQueueFile(savedItem.getQueueType());
        machineQueueFileGeneratorService.generateQueueFileForMachine(savedItem.getQueueType());

        return savedItem;
    }

    /**
     * Generuje unikalną nazwę partName, dodając suffix _2, _3, itp., jeśli nazwa już istnieje w danej queueType.
     *
     * @param queueType typ kolejki
     * @param partName nazwa części do sprawdzenia
     * @return unikalna nazwa partName
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
     *
     * @param queueType typ kolejki
     * @param partName nazwa części do sprawdzenia
     * @return true, jeśli partName istnieje, w przeciwnym razie false
     */
    private boolean isPartNameDuplicate(String queueType, String partName) {
        return productionQueueItemRepository.findByQueueType(queueType)
                .stream()
                .anyMatch(item -> {
                    String sanitizedExistingPartName = sanitizeFileName(item.getPartName(), "NoPartName_" + item.getId());
                    return sanitizedExistingPartName.equalsIgnoreCase(partName);
                });
    }

    /**
     * Wyszukuje element kolejki po identyfikatorze.
     *
     * @param id identyfikator elementu
     * @return Optional zawierający znaleziony element lub pusty, jeśli nie istnieje
     */
    public Optional<ProductionQueueItem> findById(Integer id) {
        return productionQueueItemRepository.findById(id);
    }

    /**
     * Zwraca wszystkie elementy kolejki produkcyjnej.
     *
     * @return lista wszystkich elementów kolejki
     */
    public List<ProductionQueueItem> findAll() {
        return productionQueueItemRepository.findAll();
    }

    /**
     * Aktualizuje istniejący element kolejki produkcyjnej oraz jego załączniki.
     *
     * @param id identyfikator elementu do aktualizacji
     * @param updatedItem zaktualizowane dane elementu
     * @param files nowe załączniki do dodania
     * @return zaktualizowany element kolejki
     * @throws IOException w przypadku błędu operacji na pliku
     */
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
                    String originalFileName = file.getOriginalFilename();
                    String sanitizedFileName = sanitizeFileName(originalFileName, "UNKNOWN", originalFileName.toLowerCase().endsWith(".mpf"));
                    ProductionFileInfo fileInfo = ProductionFileInfo.builder()
                            .fileName(sanitizedFileName)
                            .fileType(file.getContentType())
                            .fileSize(file.getSize())
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

            fileWatcherService.checkQueueFile(savedItem.getQueueType());
            machineQueueFileGeneratorService.generateQueueFileForMachine(savedItem.getQueueType());

            return savedItem;
        } else {
            throw new RuntimeException("Nie znaleziono elementu kolejki o ID: " + id);
        }
    }

    /**
     * Usuwa element kolejki produkcyjnej o podanym identyfikatorze.
     *
     * @param id identyfikator elementu do usunięcia
     * @throws IOException w przypadku błędu operacji na pliku
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
     *
     * @param queueType typ kolejki
     * @return lista elementów dla danego typu kolejki
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
     *
     * @param queueType typ kolejki (np. ID maszyny)
     * @throws IOException w przypadku błędu operacji na pliku
     */
    @Transactional
    public void syncWithMachine(String queueType) throws IOException {
        logger.info("Rozpoczęcie synchronizacji z maszyną dla queueType: {}", queueType);
        fileWatcherService.checkQueueFile(queueType);
        logger.info("Zakończono synchronizację z maszyną dla queueType: {}", queueType);
    }

    /**
     * Przełącza status ukończenia elementu kolejki produkcyjnej.
     *
     * @param id identyfikator elementu
     * @return zaktualizowany element kolejki
     * @throws IOException w przypadku błędu operacji na pliku
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
     *
     * @param queueType typ kolejki
     * @param items lista elementów z nową kolejnością
     * @throws IOException w przypadku błędu operacji na pliku
     */
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
            fileWatcherService.checkQueueFile(qt);
            machineQueueFileGeneratorService.generateQueueFileForMachine(qt);
        }
    }

    /**
     * Sprawdza, czy wszystkie załączniki .MPF dla elementu są ukończone.
     *
     * @param item element kolejki
     * @return true, jeśli wszystkie załączniki .MPF są ukończone, w przeciwnym razie false
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
     *
     * @param queueType typ kolejki do zweryfikowania
     * @throws IllegalArgumentException jeśli typ kolejki jest nieprawidłowy
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
     *
     * @param item element kolejki
     * @throws FileOperationException w przypadku błędu operacji na pliku
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

            String orderName = sanitizeFileName(item.getOrderName(), "NoOrderName_" + item.getId());
            String partName = sanitizeFileName(item.getPartName(), "NoPartName_" + item.getId());

            fileSystemService.synchronizeFiles(programPath, orderName, partName, item.getFiles());
        } catch (IOException e) {
            throw new FileOperationException("Nie udało się zsynchronizować załączników z katalogiem maszyny: " + e.getMessage(), e);
        }
    }

    /**
     * Sanitizuje nazwę pliku, usuwając polskie znaki i niedozwolone znaki.
     *
     * @param name nazwa do sanitizacji
     * @param defaultName domyślna nazwa w razie null/pustej wartości
     * @return sanitizowana nazwa
     */
    private String sanitizeFileName(String name, String defaultName) {
        if (name == null || name.trim().isEmpty()) {
            return defaultName;
        }
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        normalized = normalized.replaceAll("[ąĄ]", "a")
                .replaceAll("[ćĆ]", "c")
                .replaceAll("[ęĘ]", "e")
                .replaceAll("[łŁ]", "l")
                .replaceAll("[ńŃ]", "n")
                .replaceAll("[óÓ]", "o")
                .replaceAll("[śŚ]", "s")
                .replaceAll("[źŹ]", "z")
                .replaceAll("[żŻ]", "z");
        return normalized.replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_");
    }

    /**
     * Sanitizuje nazwę pliku, usuwając polskie znaki i niedozwolone znaki, z opcją skracania dla plików .MPF.
     *
     * @param name nazwa do sanitizacji
     * @param defaultName domyślna nazwa w razie null/pustej wartości
     * @param isMpf czy plik jest typu .MPF
     * @return sanitizowana nazwa
     */
    private String sanitizeFileName(String name, String defaultName, boolean isMpf) {
        if (name == null || name.trim().isEmpty()) {
            return defaultName;
        }

        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("[ąĄ]", "a")
                .replaceAll("[ćĆ]", "c")
                .replaceAll("[ęĘ]", "e")
                .replaceAll("[łŁ]", "l")
                .replaceAll("[ńŃ]", "n")
                .replaceAll("[óÓ]", "o")
                .replaceAll("[śŚ]", "s")
                .replaceAll("[źŹ]", "z")
                .replaceAll("[żŻ]", "z");

        String sanitized = normalized.replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_");

        if (isMpf) {
            String ext = ".MPF";
            String nameWithoutExt = sanitized.replaceAll("(\\.MPF)+$", "");
            String suffix = "";
            String baseName = nameWithoutExt;
            String macPattern = "_[Mm][Aa][Cc]\\d+";
            String subPattern = "_[A-Za-z]";
            String versionPattern = "_[Vv]\\d+";

            if (nameWithoutExt.matches(".*" + macPattern + subPattern + versionPattern + "$")) {
                int macIndex = nameWithoutExt.toLowerCase().lastIndexOf("_mac");
                suffix = nameWithoutExt.substring(macIndex);
                baseName = nameWithoutExt.substring(0, macIndex);
            } else if (nameWithoutExt.matches(".*" + macPattern + subPattern + "$")) {
                int macIndex = nameWithoutExt.toLowerCase().lastIndexOf("_mac");
                suffix = nameWithoutExt.substring(macIndex);
                baseName = nameWithoutExt.substring(0, macIndex);
            } else if (nameWithoutExt.matches(".*" + macPattern + "$")) {
                int macIndex = nameWithoutExt.toLowerCase().lastIndexOf("_mac");
                suffix = nameWithoutExt.substring(macIndex);
                baseName = nameWithoutExt.substring(0, macIndex);
            }

            int maxBaseLength = 24 - suffix.length() - ext.length();
            if (maxBaseLength < 0) {
                maxBaseLength = 0;
            }

            if (baseName.length() > maxBaseLength) {
                baseName = baseName.substring(0, maxBaseLength);
            }

            return baseName + suffix + ext;
        }

        return sanitized;
    }

    /**
     * Sanitizuje nazwę pliku, używając domyślnej wartości "UNKNOWN".
     *
     * @param name nazwa do sanitizacji
     * @return sanitizowana nazwa
     */
    private String sanitizeFileName(String name) {
        return sanitizeFileName(name, "UNKNOWN");
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