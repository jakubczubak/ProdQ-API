package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileImage.FileImage;
import com.example.infraboxapi.FileImage.FileImageService;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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
    private static final String CNC_ROOT_DIR_CACHE_KEY = "cncDirectoryLocations";
    private static final int DIRECTORY_SCAN_DEPTH = 3;

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
        this.machineRepository = Objects.requireNonNull(machineRepository, "MachineRepository cannot be null");
        this.fileImageService = Objects.requireNonNull(fileImageService, "FileImageService cannot be null");
        this.productionQueueItemService = Objects.requireNonNull(productionQueueItemService, "ProductionQueueItemService cannot be null");
        this.machineQueueFileGeneratorService = Objects.requireNonNull(machineQueueFileGeneratorService, "MachineQueueFileGeneratorService cannot be null");
        logger.info("MachineService initialized successfully");
    }

    @Transactional
    public Machine createMachine(MachineRequest request, MultipartFile imageFile) throws IOException {
        if (machineRepository.existsByMachineName(request.getMachineName())) {
            logger.warn("Attempt to create a machine with an existing name: {}", request.getMachineName());
            throw new IllegalArgumentException("Machine name '" + request.getMachineName() + "' already exists");
        }

        validatePath(request.getProgramPath(), "Program path");
        validatePath(request.getQueueFilePath(), "Queue file path");

        FileImage image = (imageFile != null && !imageFile.isEmpty()) ? fileImageService.createFile(imageFile) : null;

        Machine machine = Machine.builder()
                .machineName(request.getMachineName())
                .image(image)
                .programPath(normalizePath(request.getProgramPath()))
                .queueFilePath(normalizePath(request.getQueueFilePath()))
                .build();

        Machine savedMachine = machineRepository.save(machine);
        logger.info("Created machine with ID: {} and name: {}", savedMachine.getId(), savedMachine.getMachineName());

        createInitialQueueFile(savedMachine);
        return savedMachine;
    }

    private void createInitialQueueFile(Machine machine) throws IOException {
        String fileName = machine.getMachineName() + ".txt";
        Path filePath = Paths.get(machine.getQueueFilePath(), fileName);
        try {
            Files.createDirectories(filePath.getParent());
            String content = """
                    # Edytuj tylko statusy w nawiasach: [UKONCZONE] lub [NIEUKONCZONE].
                    # Przyklad: zmień '[NIEUKONCZONE]' na '[UKONCZONE]'. Nie zmieniaj ID, nazw ani innych danych!
                    # Sciezka /[orderName]/[partName]/załącznik wskazuje lokalizację programu na dysku maszyny.
                    # Bledy w formacie linii moga zostac zignorowane przez system.
                    # Wygenerowano: %s
                    # Brak programow w kolejce dla tej maszyny.
                    """.formatted(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            Files.writeString(filePath, content);
            logger.info("Created an empty queue file: {}", filePath);
        } catch (IOException e) {
            logger.error("Error creating queue file: {}", filePath, e);
            throw new IOException("Failed to create queue file for machine: " + filePath, e);
        }
    }


    public Optional<Machine> findById(Integer id) {
        return machineRepository.findById(id);
    }

    public List<Machine> findAll() {
        return machineRepository.findAll();
    }

    @Transactional
    public Machine updateMachine(Integer id, MachineRequest request, MultipartFile imageFile) throws IOException {
        Machine existingMachine = machineRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Attempt to update a non-existent machine with ID: {}", id);
                    return new RuntimeException("Machine with ID " + id + " not found");
                });

        String oldMachineName = existingMachine.getMachineName();

        if (!oldMachineName.equals(request.getMachineName()) && machineRepository.existsByMachineName(request.getMachineName())) {
            logger.warn("Attempt to change machine name to an already existing one: {}", request.getMachineName());
            throw new IllegalArgumentException("Machine name '" + request.getMachineName() + "' already exists");
        }

        validatePath(request.getProgramPath(), "Program path");
        validatePath(request.getQueueFilePath(), "Queue file path");

        existingMachine.setMachineName(request.getMachineName());
        existingMachine.setProgramPath(normalizePath(request.getProgramPath()));
        existingMachine.setQueueFilePath(normalizePath(request.getQueueFilePath()));

        if (!oldMachineName.equals(request.getMachineName())) {
            handleQueueFileOnRename(existingMachine, oldMachineName);
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            FileImage newImage = fileImageService.updateFile(imageFile, existingMachine.getImage());
            existingMachine.setImage(newImage);
        }

        return machineRepository.save(existingMachine);
    }

    private void handleQueueFileOnRename(Machine machine, String oldMachineName) throws IOException {
        Path oldFilePath = Paths.get(machine.getQueueFilePath(), oldMachineName + ".txt");
        try {
            Files.deleteIfExists(oldFilePath);
            logger.info("Deleted old queue file: {}", oldFilePath);
        } catch (IOException e) {
            logger.error("Error deleting old queue file {}: {}", oldFilePath, e.getMessage(), e);
        }

        try {
            machineQueueFileGeneratorService.generateQueueFileForMachine(String.valueOf(machine.getId()));
            logger.info("Generated new queue file for updated machine name: {}", machine.getMachineName());
        } catch (IOException e) {
            logger.error("Error generating new queue file for machine ID {}: {}", machine.getId(), e.getMessage(), e);
            throw new IOException("Failed to generate new queue file after renaming machine", e);
        }
    }

    @Transactional
    public void deleteById(Integer id) {
        machineRepository.findById(id).ifPresent(machine -> {
            Path filePath = Paths.get(machine.getQueueFilePath(), machine.getMachineName() + ".txt");
            try {
                Files.deleteIfExists(filePath);
                logger.info("Deleted queue file (if it existed): {}", filePath);
            } catch (IOException e) {
                logger.error("Error while deleting queue file: {}", filePath, e);
            }
            machineRepository.deleteById(id);
            logger.info("Machine with ID: {} has been deleted", id);
        });
    }

    public List<String> getAvailableLocations() {
        return getDirectoryLocations();
    }

    public String getDirectoryStructureHash() {
        List<String> locations = getDirectoryLocations();
        return computeDirectoryStructureHash(locations);
    }

    /**
     * This is the core method for scanning directories. It is resistant to AccessDeniedException.
     * It retrieves directory locations from the cache or scans the filesystem if the cache is empty.
     */
    private List<String> getDirectoryLocations() {
        List<String> cachedLocations = locationsCache.getIfPresent(CNC_ROOT_DIR_CACHE_KEY);
        if (cachedLocations != null) {
            logger.info("Returning {} cached locations.", cachedLocations.size());
            return cachedLocations;
        }

        Path mountDir = getMountDir();
        logger.debug("Cache miss. Starting directory scan for: {}", mountDir);
        long startTime = System.nanoTime();

        if (!Files.exists(mountDir) || !Files.isDirectory(mountDir) || !Files.isReadable(mountDir)) {
            logger.warn("Root directory {} does not exist or is not accessible.", mountDir);
            return new ArrayList<>();
        }

        final List<String> locations = new ArrayList<>();
        try {
            Files.walkFileTree(mountDir, new AccessGuardedFileVisitor(locations, mountDir));

            List<String> sortedLocations = locations.stream().sorted().collect(Collectors.toList());
            locationsCache.put(CNC_ROOT_DIR_CACHE_KEY, sortedLocations);

            long duration = (System.nanoTime() - startTime) / 1_000_000; // ms
            logger.info("Scanned and cached {} locations. Execution time: {} ms", sortedLocations.size(), duration);
            return sortedLocations;

        } catch (IOException e) {
            logger.error("A critical error occurred while walking the file tree: {}", mountDir, e);
            throw new RuntimeException("Failed to scan directory structure due to a critical error.", e);
        }
    }

    /**
     * A FileVisitor that ignores directories it cannot access (AccessDeniedException)
     * instead of terminating the entire walk.
     */
    private class AccessGuardedFileVisitor extends SimpleFileVisitor<Path> {
        private final List<String> locations;
        private final Path mountDir;

        public AccessGuardedFileVisitor(List<String> locations, Path mountDir) {
            this.locations = locations;
            this.mountDir = mountDir;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            // Limit directory depth
            if (mountDir.relativize(dir).getNameCount() >= DIRECTORY_SCAN_DEPTH) {
                return FileVisitResult.SKIP_SUBTREE;
            }

            String relativePath = relativizePath(dir.toString(), mountDir);
            if (relativePath != null) {
                locations.add(relativePath);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            if (exc instanceof AccessDeniedException) {
                logger.warn("Access denied to directory, skipping: {}. Reason: {}", file, exc.getMessage());
                // If access is denied to a directory, we skip its entire subtree.
                return FileVisitResult.SKIP_SUBTREE;
            }
            logger.error("Error visiting file/directory: {}", file, exc);
            return FileVisitResult.CONTINUE;
        }
    }


    private String computeDirectoryStructureHash(List<String> locations) {
        if (locations == null || locations.isEmpty()) {
            return "empty";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            String combined = String.join(";", locations); // Use a separator for robustness
            byte[] hashBytes = digest.digest(combined.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("MD5 algorithm not found!", e);
            throw new RuntimeException("Failed to compute directory structure hash", e);
        }
    }

    private Path getMountDir() {
        String appEnv = System.getenv("APP_ENV");
        boolean isProdEnv = "prod".equalsIgnoreCase(appEnv) || "docker-local".equalsIgnoreCase(appEnv);
        return isProdEnv ? Paths.get("/cnc") : Paths.get("./cnc");
    }

    private String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) return "";
        try {
            Path normalizedPath = Paths.get(path).normalize();
            String result = normalizedPath.toString().replaceFirst("^[A-Za-z]:", "");
            return result.replace("\\", "/");
        } catch (InvalidPathException e) {
            logger.error("Failed to normalize invalid path: {}", path, e);
            return "";
        }
    }

    private String relativizePath(String path, Path mountDir) {
        try {
            if (path == null || mountDir == null) return null;
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
        Path mountDir = getMountDir();
        Path resolvedPath = cleanedPath.isEmpty() ? mountDir : mountDir.resolve(cleanedPath).normalize();

        if (!Files.exists(resolvedPath)) {
            throw new IllegalArgumentException(fieldName + " does not exist: " + resolvedPath);
        }
        if (!Files.isDirectory(resolvedPath)) {
            throw new IllegalArgumentException(fieldName + " is not a directory: " + resolvedPath);
        }
        if (!Files.isReadable(resolvedPath) || !Files.isWritable(resolvedPath)) {
            throw new IllegalArgumentException(fieldName + " is not accessible (read/write permissions required): " + resolvedPath);
        }
    }

    public ResponseEntity<byte[]> downloadMachinePrograms(Integer machineId) throws IOException {
        Machine machine = findById(machineId).orElse(null);
        if (machine == null) {
            return ResponseEntity.notFound().build();
        }

        List<ProductionQueueItem> programs = productionQueueItemService.findByQueueType(String.valueOf(machineId), Pageable.unpaged()).getContent();

        if (programs.stream().allMatch(p -> p.getFiles().isEmpty())) {
            return ResponseEntity.noContent().build();
        }

        byte[] zipBytes = createZipArchive(programs, machine.getMachineName());
        String zipFileName = machine.getMachineName().replaceAll("[^a-zA-Z0-9_\\-.]", "_") + ".zip";

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipBytes);
    }

    private byte[] createZipArchive(List<ProductionQueueItem> programs, String machineName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (ProductionQueueItem program : programs) {
                String orderName = sanitizeFileName(program.getOrderName(), "NoOrderName_" + program.getId());
                String partName = sanitizeFileName(program.getPartName(), "NoPartName_" + program.getId());

                for (ProductionFileInfo file : program.getFiles()) {
                    String fileName = file.getFileName();
                    String entryPath = String.format("%s/%s/%s/%s", machineName, orderName, partName, fileName);

                    zos.putNextEntry(new ZipEntry(entryPath));

                    Path filePath = Paths.get(file.getFilePath());
                    if (Files.exists(filePath) && Files.isReadable(filePath)) {
                        Files.copy(filePath, zos);
                    }

                    zos.closeEntry();
                }
            }
        }
        return baos.toByteArray();
    }

    private String sanitizeFileName(String name, String defaultName) {
        if (name == null || name.trim().isEmpty()) {
            return defaultName;
        }
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}