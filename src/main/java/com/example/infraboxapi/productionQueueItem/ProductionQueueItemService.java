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
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Serwis odpowiedzialny za zarządzanie elementami kolejki produkcyjnej, w tym zapisywanie, aktualizowanie, usuwanie i synchronizację załączników.
 */
@Service
public class ProductionQueueItemService {

    private static final Logger logger = LoggerFactory.getLogger(ProductionQueueItemService.class);

    private final ProductionQueueItemRepository productionQueueItemRepository;
    private final ProductionFileInfoService productionFileInfoService;
    private final MachineRepository machineRepository;
    private final MachineQueueFileGeneratorService machineQueueFileGeneratorService;
    private final FileWatcherService fileWatcherService;

    @Autowired
    public ProductionQueueItemService(
            ProductionQueueItemRepository productionQueueItemRepository,
            ProductionFileInfoService productionFileInfoService,
            MachineRepository machineRepository,
            MachineQueueFileGeneratorService machineQueueFileGeneratorService,
            FileWatcherService fileWatcherService) {
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.productionFileInfoService = productionFileInfoService;
        this.machineRepository = machineRepository;
        this.machineQueueFileGeneratorService = machineQueueFileGeneratorService;
        this.fileWatcherService = fileWatcherService;
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
        // Najpierw sprawdź statusy w pliku kolejki, potem wygeneruj nowy
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
                fileWatcherService.checkQueueFile(oldQueueType);
                machineQueueFileGeneratorService.generateQueueFileForMachine(oldQueueType);
            }
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
            deleteAttachmentsFromMachinePath(item);
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
        // Zwróć listę elementów bez synchronizacji automatycznej
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
            syncAttachmentsToMachinePath(savedItem);
            fileWatcherService.checkQueueFile(savedItem.getQueueType());
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
            String oldQueueType = oldQueueTypes.get(item.getId());
            if (oldQueueType != null && !oldQueueType.equals(queueType) && !"ncQueue".equals(oldQueueType) && !"completed".equals(oldQueueType)) {
                deleteAttachmentsFromMachinePath(item, oldQueueType, queueType);
            }
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
            throw new FileOperationException("Nie udało się zsynchronizować załączników z katalogiem maszyny: " + e.getMessage(), e);
        }
    }

    /**
     * Usuwa załączniki elementu z katalogu maszyny dla starego typu kolejki.
     *
     * @param item element kolejki
     * @param oldQueueType stary typ kolejki
     * @param newQueueType nowy typ kolejki
     * @throws FileOperationException w przypadku błędu operacji na pliku
     */
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
            throw new FileOperationException("Nie udało się usunąć starych załączników: " + e.getMessage(), e);
        }
    }

    /**
     * Usuwa załączniki elementu z katalogu maszyny.
     *
     * @param item element kolejki
     * @throws FileOperationException w przypadku błędu operacji na pliku
     */
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
            throw new FileOperationException("Nie udało się usunąć załączników: " + e.getMessage(), e);
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
        String sanitized = normalized.replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_");

        if (isMpf) {
            // Dla plików .MPF: skróć nazwę do 24 znaków (bez rozszerzenia)
            String ext = ".MPF";
            String nameWithoutExt = sanitized.replaceFirst("(\\.[^\\.]+)$", "");
            if (nameWithoutExt.length() > 24) {
                nameWithoutExt = nameWithoutExt.substring(0, 24);
            }
            return nameWithoutExt + ext;
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
     * Generuje unikalną ścieżkę dla pliku, dodając numer wersji w razie konfliktu nazw.
     *
     * @param basePath katalog bazowy
     * @param fileName nazwa pliku
     * @return unikalna ścieżka do pliku
     * @throws IOException w przypadku braku możliwości znalezienia unikalnej nazwy
     */
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
                throw new FileOperationException("Nie można znaleźć unikalnej nazwy dla pliku: " + fileName);
            }
        }
    }

    /**
     * Sprawdza, czy katalog jest pusty.
     *
     * @param dir ścieżka do katalogu
     * @return true, jeśli katalog jest pusty, w przeciwnym razie false
     * @throws IOException w przypadku błędu operacji na pliku
     */
    private boolean isDirectoryEmpty(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return !entries.findFirst().isPresent();
        }
    }

    /**
     * Rekurencyjnie usuwa katalog i jego zawartość.
     *
     * @param path ścieżka do katalogu
     * @throws IOException w przypadku błędu operacji na pliku
     */
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