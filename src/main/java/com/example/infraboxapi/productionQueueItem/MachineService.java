package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileImage.FileImage;
import com.example.infraboxapi.FileImage.FileImageService;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final FileSystemService fileSystemService;
    private final Cache<String, List<String>> locationsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    public MachineService(
            MachineRepository machineRepository,
            FileImageService fileImageService,
            ProductionQueueItemService productionQueueItemService,
            MachineQueueFileGeneratorService machineQueueFileGeneratorService,
            FileSystemService fileSystemService) {
        this.machineRepository = Objects.requireNonNull(machineRepository, "MachineRepository cannot be null");
        this.fileImageService = Objects.requireNonNull(fileImageService, "FileImageService cannot be null");
        this.productionQueueItemService = Objects.requireNonNull(productionQueueItemService, "ProductionQueueItemService cannot be null");
        this.machineQueueFileGeneratorService = Objects.requireNonNull(machineQueueFileGeneratorService, "MachineQueueFileGeneratorService cannot be null");
        this.fileSystemService = Objects.requireNonNull(fileSystemService, "FileSystemService cannot be null");
        logger.info("MachineService initialized successfully");
    }

    /**
     * Runs cleanup of orphaned queue files on application startup.
     * This method deletes .txt queue files that no longer correspond to existing machines.
     */
    @PostConstruct
    @Async
    public void cleanupOrphanedQueueFilesOnStartup() {
        logger.info("Starting cleanup of orphaned queue files on application startup...");
        try {
            int deletedFiles = cleanupOrphanedQueueFiles();
            logger.info("Finished cleanup of orphaned queue files. Deleted {} files.", deletedFiles);
        } catch (Exception e) {
            logger.error("Error during cleanup of orphaned queue files: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleans up orphaned queue files (.txt) that don't correspond to any existing machine.
     * Only scans directories that are explicitly configured in machine settings.
     * SAFE: Does NOT scan any directories if no machines are configured.
     *
     * @return Number of deleted files
     */
    public int cleanupOrphanedQueueFiles() {
        int deletedCount = 0;
        List<Machine> allMachines = machineRepository.findAll();

        // SAFETY CHECK: If no machines exist, skip cleanup entirely
        // We don't want to blindly delete files from unknown locations
        if (allMachines.isEmpty()) {
            logger.info("No machines configured. Skipping queue files cleanup for safety.");
            return 0;
        }

        // Build a set of expected queue file names (sanitized machine names + .txt)
        Set<String> expectedFileNames = allMachines.stream()
                .map(machine -> fileSystemService.sanitizeName(machine.getMachineName(), "machine_queue") + ".txt")
                .collect(Collectors.toSet());

        logger.debug("Expected queue files: {}", expectedFileNames);

        // Collect all unique queue file paths from configured machines
        Set<String> uniqueQueuePaths = allMachines.stream()
                .map(Machine::getQueueFilePath)
                .filter(Objects::nonNull)
                .map(this::normalizePath)
                .collect(Collectors.toSet());

        if (uniqueQueuePaths.isEmpty()) {
            logger.info("No queue file paths configured. Skipping cleanup.");
            return 0;
        }

        // Scan ONLY configured queue file paths
        for (String queuePathStr : uniqueQueuePaths) {
            Path queuePath = resolveMountedPath(queuePathStr);

            if (!Files.exists(queuePath) || !Files.isDirectory(queuePath)) {
                logger.warn("Queue file path {} does not exist or is not a directory. Skipping.", queuePath);
                continue;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(queuePath, "*.txt")) {
                for (Path file : stream) {
                    String fileName = file.getFileName().toString();

                    // If this .txt file is NOT in the expected list, delete it
                    if (!expectedFileNames.contains(fileName)) {
                        try {
                            if (Files.deleteIfExists(file)) {
                                logger.info("Deleted orphaned queue file: {}", file);
                                deletedCount++;
                            }
                        } catch (IOException e) {
                            logger.error("Failed to delete orphaned queue file {}: {}", file, e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Error scanning path {}: {}", queuePath, e.getMessage(), e);
            }
        }

        return deletedCount;
    }

    /**
     * Resolves a path to the mounted directory structure.
     * Uses /cnc for production/docker-local, ./cnc for local development.
     */
    private Path resolveMountedPath(String pathStr) {
        String cleanedPath = pathStr.replaceFirst("^/+", "").replaceFirst("^cnc/?", "");
        String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local";
        Path mountDir = "prod".equalsIgnoreCase(appEnv) || "docker-local".equalsIgnoreCase(appEnv)
                ? Paths.get("/cnc")
                : Paths.get("./cnc");

        return cleanedPath.isEmpty() ? mountDir : mountDir.resolve(cleanedPath).normalize();
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
        try {
            logger.info("Creating initial queue file for machine: {}", machine.getMachineName());
            machineQueueFileGeneratorService.generateQueueFileForMachine(String.valueOf(machine.getId()));
            logger.info("Successfully created initial queue file for machine ID: {}", machine.getId());
        } catch (IOException e) {
            logger.error("Error creating initial queue file for machine {}: {}", machine.getId(), e.getMessage(), e);
            throw new IOException("Failed to create initial queue file for machine: " + machine.getMachineName(), e);
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

    private class AccessGuardedFileVisitor extends SimpleFileVisitor<Path> {
        private final List<String> locations;
        private final Path mountDir;

        public AccessGuardedFileVisitor(List<String> locations, Path mountDir) {
            this.locations = locations;
            this.mountDir = mountDir;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
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
            String combined = String.join(";", locations);
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

        byte[] zipBytes = createZipArchive(programs, machine);
        String zipFileName = fileSystemService.sanitizeName(machine.getMachineName(), "archive") + ".zip";

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipBytes);
    }

    private byte[] createZipArchive(List<ProductionQueueItem> programs, Machine machine) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            String queueContent = machineQueueFileGeneratorService.generateQueueFileForMachine(String.valueOf(machine.getId()));

            if (queueContent != null && !queueContent.isEmpty()) {
                ZipEntry queueFileEntry = new ZipEntry(fileSystemService.sanitizeName(machine.getMachineName(), "queue") + ".zip");
                zos.putNextEntry(queueFileEntry);
                zos.write(queueContent.getBytes());
                zos.closeEntry();
            }

            for (ProductionQueueItem program : programs) {
                String orderName = fileSystemService.sanitizeName(program.getOrderName(), "NoOrderName_" + program.getId());
                String partName = fileSystemService.sanitizeName(program.getPartName(), "NoPartName_" + program.getId());

                for (ProductionFileInfo file : program.getFiles()) {
                    String fileName = file.getFileName();
                    String entryPath = String.format("%s/%s/%s", orderName, partName, fileName);

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
}