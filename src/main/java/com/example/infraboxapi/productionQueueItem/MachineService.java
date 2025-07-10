package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileImage.FileImage;
import com.example.infraboxapi.FileImage.FileImageService;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable; // Dodany import
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class MachineService {

    private static final Logger logger = LoggerFactory.getLogger(MachineService.class);

    private final MachineRepository machineRepository;
    private final FileImageService fileImageService;
    private final ProductionQueueItemService productionQueueItemService;
    private final MachineQueueFileGeneratorService machineQueueFileGeneratorService;
    private final Cache<String, List<String>> locationsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    public MachineService(
            MachineRepository machineRepository,
            FileImageService fileImageService,
            ProductionQueueItemService productionQueueItemService,
            MachineQueueFileGeneratorService machineQueueFileGeneratorService) {
        try {
            this.machineRepository = Objects.requireNonNull(machineRepository, "MachineRepository cannot be null");
            this.fileImageService = Objects.requireNonNull(fileImageService, "FileImageService cannot be null");
            this.productionQueueItemService = Objects.requireNonNull(productionQueueItemService, "ProductionQueueItemService cannot be null");
            this.machineQueueFileGeneratorService = Objects.requireNonNull(machineQueueFileGeneratorService, "MachineQueueFileGeneratorService cannot be null");
            logger.info("MachineService initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize MachineService", e);
            throw new RuntimeException("Failed to initialize MachineService", e);
        }
    }

    @Transactional
    public Machine createMachine(MachineRequest request, MultipartFile imageFile) throws IOException {
        if (machineRepository.existsByMachineName(request.getMachineName())) {
            logger.warn("Próba utworzenia maszyny o istniejącej nazwie: {}", request.getMachineName());
            throw new IllegalArgumentException("Machine name '" + request.getMachineName() + "' already exists");
        }

        validatePath(request.getProgramPath(), "Program path");
        validatePath(request.getQueueFilePath(), "Queue file path");

        FileImage image = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            image = fileImageService.createFile(imageFile);
        }

        Machine machine = Machine.builder()
                .machineName(request.getMachineName())
                .image(image)
                .programPath(normalizePath(request.getProgramPath()))
                .queueFilePath(normalizePath(request.getQueueFilePath()))
                .build();

        Machine savedMachine = machineRepository.save(machine);
        logger.info("Utworzono maszynę o ID: {} i nazwie: {}", savedMachine.getId(), savedMachine.getMachineName());

        String fileName = savedMachine.getMachineName() + ".txt";
        Path filePath = Paths.get(savedMachine.getQueueFilePath(), fileName);
        try {
            Files.createDirectories(filePath.getParent());
            StringBuilder content = new StringBuilder();
            content.append("# Edytuj tylko statusy w nawiasach: [UKONCZONE] lub [NIEUKONCZONE].\n");
            content.append("# Przyklad: zmień '[NIEUKONCZONE]' na '[UKONCZONE]'. Nie zmieniaj ID, nazw ani innych danych!\n");
            content.append("# Sciezka /[orderName]/[partName]/załącznik wskazuje lokalizację programu na dysku maszyny.\n");
            content.append("# Bledy w formacie linii moga zostac zignorowane przez system.\n");
            content.append(String.format("# Wygenerowano: %s\n",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            content.append("# Brak programow w kolejce dla tej maszyny.\n");

            Files.writeString(filePath, content.toString());
            logger.info("Utworzono pusty plik kolejki: {}", filePath);
        } catch (IOException e) {
            logger.error("Błąd podczas tworzenia pliku kolejki: {}", filePath, e);
            throw new IOException("Nie udało się utworzyć pliku kolejki dla maszyny: " + filePath, e);
        }

        return savedMachine;
    }

    public Optional<Machine> findById(Integer id) {
        return machineRepository.findById(id);
    }

    public List<Machine> findAll() {
        return machineRepository.findAll();
    }

    @Transactional
    public Machine updateMachine(Integer id, MachineRequest request, MultipartFile imageFile) throws IOException {
        Optional<Machine> existingMachineOpt = machineRepository.findById(id);
        if (existingMachineOpt.isEmpty()) {
            logger.warn("Próba aktualizacji nieistniejącej maszyny o ID: {}", id);
            throw new RuntimeException("Machine with ID " + id + " not found");
        }

        Machine existingMachine = existingMachineOpt.get();
        String oldMachineName = existingMachine.getMachineName();

        if (!oldMachineName.equals(request.getMachineName()) &&
                machineRepository.existsByMachineName(request.getMachineName())) {
            logger.warn("Próba zmiany nazwy maszyny na już istniejącą: {}", request.getMachineName());
            throw new IllegalArgumentException("Machine name '" + request.getMachineName() + "' already exists");
        }

        validatePath(request.getProgramPath(), "Program path");
        validatePath(request.getQueueFilePath(), "Queue file path");

        existingMachine.setMachineName(request.getMachineName());
        existingMachine.setProgramPath(normalizePath(request.getProgramPath()));
        existingMachine.setQueueFilePath(normalizePath(request.getQueueFilePath()));

        if (!oldMachineName.equals(request.getMachineName())) {
            String oldFileName = oldMachineName + ".txt";
            Path oldFilePath = Paths.get(existingMachine.getQueueFilePath(), oldFileName);
            try {
                if (Files.exists(oldFilePath)) {
                    Files.delete(oldFilePath);
                    logger.info("Deleted old queue file: {}", oldFilePath);
                }
            } catch (IOException e) {
                logger.error("Error deleting old queue file {}: {}", oldFilePath, e.getMessage(), e);
            }

            try {
                machineQueueFileGeneratorService.generateQueueFileForMachine(String.valueOf(id));
                logger.info("Generated new queue file for updated machine name: {}", request.getMachineName());
            } catch (IOException e) {
                logger.error("Error generating new queue file for machine ID {}: {}", id, e.getMessage(), e);
                throw new IOException("Failed to generate new queue file after renaming machine", e);
            }
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            FileImage newImage = fileImageService.updateFile(imageFile, existingMachine.getImage());
            existingMachine.setImage(newImage);
        }

        return machineRepository.save(existingMachine);
    }

    @Transactional
    public void deleteById(Integer id) {
        Optional<Machine> machineOpt = machineRepository.findById(id);
        if (machineOpt.isEmpty()) {
            logger.warn("Próba usunięcia nieistniejącej maszyny o ID: {}", id);
            return;
        }

        Machine machine = machineOpt.get();
        String fileName = machine.getMachineName() + ".txt";
        Path filePath = Paths.get(machine.getQueueFilePath(), fileName);
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                logger.info("Usunięto plik kolejki: {}", filePath);
            } else {
                logger.info("Plik kolejki nie istnieje: {}", filePath);
            }
        } catch (IOException e) {
            logger.error("Błąd podczas usuwania pliku kolejki: {}", filePath, e);
        }

        machineRepository.deleteById(id);
        logger.info("Maszyna o ID: {} została usunięta", id);
    }

    public List<String> getAvailableLocations() {
        String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local";
        Path mountDir = "prod".equalsIgnoreCase(appEnv) || "docker-local".equalsIgnoreCase(appEnv)
                ? Paths.get("/cnc")
                : Paths.get("./cnc");
        String tempCacheKey = mountDir.toString();

        List<String> cachedLocations = locationsCache.getIfPresent(tempCacheKey);
        if (cachedLocations != null) {
            logger.info("Returning cached locations for key: {}", tempCacheKey);
            return cachedLocations;
        }

        try {
            logger.debug("Starting directory scan for mountDir: {}", mountDir);
            long startTime = System.nanoTime();
            List<String> locations = new ArrayList<>();
            if (!Files.exists(mountDir) || !Files.isDirectory(mountDir) || !Files.isReadable(mountDir) || !Files.isWritable(mountDir)) {
                logger.warn("Root directory {} does not exist or is not accessible", mountDir);
                return locations;
            }

            try (var paths = Files.walk(mountDir, 3)) { // Limit to 3 levels
                locations = paths
                        .filter(Files::isDirectory)
                        .map(Path::toString)
                        .collect(Collectors.toList());
                logger.info("Processed {} directories", locations.size());
            } catch (IOException e) {
                logger.error("Error walking directory structure: {}", mountDir, e);
                throw new RuntimeException("Failed to scan directory structure", e);
            }

            List<String> normalizedLocations = locations.stream()
                    .map(path -> relativizePath(path, mountDir))
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());

            String finalCacheKey = computeDirectoryStructureHash(normalizedLocations);
            locationsCache.put(finalCacheKey, normalizedLocations);
            locationsCache.put(tempCacheKey, normalizedLocations);
            long duration = (System.nanoTime() - startTime) / 1_000_000; // ms
            logger.info("Returning locations: {}, execution time: {} ms", normalizedLocations.size(), duration);
            return normalizedLocations;
        } catch (Exception e) {
            logger.error("Unexpected error in getAvailableLocations", e);
            throw new RuntimeException("Failed to retrieve available locations", e);
        }
    }

    public String getDirectoryStructureHash() {
        String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local";
        Path mountDir = "prod".equalsIgnoreCase(appEnv) || "docker-local".equalsIgnoreCase(appEnv)
                ? Paths.get("/cnc")
                : Paths.get("./cnc");
        String tempCacheKey = mountDir.toString();

        List<String> cachedLocations = locationsCache.getIfPresent(tempCacheKey);
        if (cachedLocations != null) {
            logger.info("Using cached locations for hash computation: {}", tempCacheKey);
            return computeDirectoryStructureHash(cachedLocations);
        }

        try {
            logger.debug("Computing directory structure hash for mountDir: {}", mountDir);
            List<String> locations = new ArrayList<>();
            if (!Files.exists(mountDir) || !Files.isDirectory(mountDir) || !Files.isReadable(mountDir) || !Files.isWritable(mountDir)) {
                logger.warn("Root directory {} does not exist or is not accessible", mountDir);
                return computeDirectoryStructureHash(locations);
            }

            try (var paths = Files.walk(mountDir, 3)) {
                locations = paths
                        .filter(Files::isDirectory)
                        .map(Path::toString)
                        .collect(Collectors.toList());
                logger.info("Processed {} directories for hash", locations.size());
            } catch (IOException e) {
                logger.error("Error walking directory structure for hash: {}", mountDir, e);
                throw new RuntimeException("Failed to compute directory structure hash", e);
            }

            List<String> normalizedLocations = locations.stream()
                    .map(path -> relativizePath(path, mountDir))
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());

            String hash = computeDirectoryStructureHash(normalizedLocations);
            locationsCache.put(hash, normalizedLocations);
            locationsCache.put(tempCacheKey, normalizedLocations);
            return hash;
        } catch (Exception e) {
            logger.error("Unexpected error in getDirectoryStructureHash", e);
            throw new RuntimeException("Failed to compute directory structure hash", e);
        }
    }

    private String computeDirectoryStructureHash(List<String> locations) {
        try {
            logger.debug("Computing hash for {} locations", locations.size());
            MessageDigest digest = MessageDigest.getInstance("MD5");
            List<String> sortedLocations = new ArrayList<>(locations);
            sortedLocations.sort(String::compareTo);
            String combined = String.join("", sortedLocations);
            byte[] hashBytes = digest.digest(combined.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Error computing directory structure hash", e);
            throw new RuntimeException("Failed to compute directory structure hash", e);
        }
    }

    private String normalizePath(String path) {
        if (path == null) return "";
        try {
            Path normalizedPath = Paths.get(path).normalize();
            String result = normalizedPath.toString().replaceFirst("^[A-Za-z]:", "");
            return result.replace("\\", "/");
        } catch (Exception e) {
            logger.error("Failed to normalize path: {}", path, e);
            return "";
        }
    }

    private String relativizePath(String path, Path mountDir) {
        try {
            if (path == null || mountDir == null) {
                logger.warn("Null path or mountDir in relativizePath: path={}, mountDir={}", path, mountDir);
                return null;
            }
            Path normalizedPath = Paths.get(path).normalize();
            Path relativePath = mountDir.relativize(normalizedPath);
            String result = relativePath.toString().replace("\\", "/");
            return result.isEmpty() ? "cnc" : "cnc/" + result;
        } catch (Exception e) {
            logger.error("Failed to relativize path: path={}, mountDir={}", path, mountDir, e);
            return null;
        }
    }

    private void validatePath(String path, String fieldName) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        if (path.matches(".*[:*?\"<>|].*")) {
            throw new IllegalArgumentException(fieldName + " contains illegal characters: " + path);
        }
        String cleanedPath = path.replaceFirst("^/+", "").replaceFirst("^cnc/?", "");
        String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local";
        Path mountDir = "prod".equalsIgnoreCase(appEnv) || "docker-local".equalsIgnoreCase(appEnv)
                ? Paths.get("/cnc")
                : Paths.get("./cnc");
        Path resolvedPath = cleanedPath.isEmpty() ? mountDir : mountDir.resolve(cleanedPath).normalize();
        logger.info("Validating path {}: cleanedPath={}, resolvedPath={}, exists={}, isDirectory={}",
                path, cleanedPath, resolvedPath, Files.exists(resolvedPath), Files.isDirectory(resolvedPath));
        if (!Files.exists(resolvedPath)) {
            throw new IllegalArgumentException(fieldName + " does not exist: " + path);
        }
        if (!Files.isDirectory(resolvedPath)) {
            throw new IllegalArgumentException(fieldName + " is not a directory: " + path);
        }
        if (!Files.isReadable(resolvedPath) || !Files.isWritable(resolvedPath)) {
            throw new IllegalArgumentException(fieldName + " is not accessible (read/write permissions required): " + path);
        }
    }

    public ResponseEntity<byte[]> downloadMachinePrograms(Integer machineId) throws IOException {
        Optional<Machine> machineOpt = findById(machineId);
        if (machineOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Machine machine = machineOpt.get();
        // --- TUTAJ JEST POPRAWKA ---
        List<ProductionQueueItem> programs = productionQueueItemService.findByQueueType(String.valueOf(machineId), Pageable.unpaged()).getContent();

        if (programs.isEmpty() || programs.stream().noneMatch(program -> !program.getFiles().isEmpty())) {
            return ResponseEntity.noContent().build();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (ProductionQueueItem program : programs) {
                String orderName = program.getOrderName() != null && !program.getOrderName().isEmpty()
                        ? program.getOrderName()
                        : "NoOrderName_" + program.getId();
                orderName = orderName.replaceAll("[^a-zA-Z0-9_\\-]", "_");

                String partName = program.getPartName() != null && !program.getPartName().isEmpty()
                        ? program.getPartName()
                        : "NoPartName_" + program.getId();
                partName = partName.replaceAll("[^a-zA-Z0-9_\\-]", "_");

                for (ProductionFileInfo file : program.getFiles()) {
                    String fileName = file.getFileName();
                    String entryPath = String.format("%s/%s/%s/%s", machine.getMachineName(), orderName, partName, fileName);
                    entryPath = entryPath.replaceAll("[^a-zA-Z0-9_\\-/\\.]", "_");

                    ZipEntry zipEntry = new ZipEntry(entryPath);
                    zos.putNextEntry(zipEntry);

                    // --- TUTAJ JEST DRUGA POPRAWKA ---
                    // Odczytujemy plik z jego ścieżki, zamiast używać nieistniejącej metody getFileContent()
                    Path filePath = Paths.get(file.getFilePath());
                    if (Files.exists(filePath)) {
                        byte[] fileBytes = Files.readAllBytes(filePath);
                        zos.write(fileBytes);
                    }

                    zos.closeEntry();
                }
            }
            zos.finish();
        }

        byte[] zipBytes = baos.toByteArray();
        String zipFileName = machine.getMachineName() + ".zip";
        zipFileName = zipFileName.replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_");

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipBytes);
    }
}