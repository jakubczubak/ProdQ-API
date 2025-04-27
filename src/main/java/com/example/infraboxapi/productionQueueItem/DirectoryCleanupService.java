package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.notification.NotificationDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for intelligently cleaning up unused directories in the machine's programPath structure,
 * handling locked files and sending system notifications.
 */
@Service
public class DirectoryCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryCleanupService.class);

    private final MachineRepository machineRepository;
    private final ProductionQueueItemRepository productionQueueItemRepository;
    private final FileSystemService fileSystemService;
    private final BlockedDirectoryRepository blockedDirectoryRepository;
    private final NotificationService notificationService;

    public DirectoryCleanupService(
            MachineRepository machineRepository,
            ProductionQueueItemRepository productionQueueItemRepository,
            FileSystemService fileSystemService,
            BlockedDirectoryRepository blockedDirectoryRepository,
            NotificationService notificationService) {
        this.machineRepository = machineRepository;
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.fileSystemService = fileSystemService;
        this.blockedDirectoryRepository = blockedDirectoryRepository;
        this.notificationService = notificationService;
    }

    /**
     * Cleans up unused directories for all machines and processes blocked directories stored in the database.
     * This method is invoked automatically after application startup.
     */
    @Async
    @Transactional
    public void cleanupAllMachines() {
        logger.info("Started cleanup of unused directories for all machines.");
        List<Machine> machines = machineRepository.findAll();
        int totalDeleted = 0;
        int totalBlocked = 0;

        for (Machine machine : machines) {
            try {
                CleanupResult result = cleanUnusedDirectories(machine.getProgramPath(), String.valueOf(machine.getId()));
                totalDeleted += result.deletedDirectories;
                totalBlocked += result.blockedDirectories;
            } catch (IOException e) {
                logger.error("Error during directory cleanup for machine {}: {}", machine.getId(), e.getMessage(), e);
            }
        }

        // Process blocked directories
        CleanupResult blockedResult = processBlockedDirectories();
        totalDeleted += blockedResult.deletedDirectories;
        totalBlocked += blockedResult.blockedDirectories;

        // Send system notification
        sendCleanupNotification(totalDeleted, totalBlocked);

        logger.info("Completed cleanup of unused directories. Deleted: {}, Blocked: {}", totalDeleted, totalBlocked);
    }

    /**
     * Cleans unused directories in the specified programPath for the given queueType.
     *
     * @param programPath Path to the machine's program directory
     * @param queueType   Machine ID (as String)
     * @return Cleanup result (number of deleted and blocked directories)
     * @throws IOException If a filesystem operation fails
     */
    @Transactional
    public CleanupResult cleanUnusedDirectories(String programPath, String queueType) throws IOException {
        logger.info("Cleaning unused directories for queueType: {} in path: {}", queueType, programPath);
        int deletedDirectories = 0;
        int blockedDirectories = 0;

        // Retrieve active orderName/partName pairs for all machines using this programPath
        Set<String> activePaths = getActiveDirectoryPathsForProgramPath(programPath);
        Path basePath = Paths.get(programPath).toAbsolutePath().normalize();

        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            logger.debug("Directory {} does not exist or is not a directory", basePath);
            return new CleanupResult(deletedDirectories, blockedDirectories);
        }

        // Scan orderName directories
        try (DirectoryStream<Path> orderStream = Files.newDirectoryStream(basePath)) {
            for (Path orderPath : orderStream) {
                if (!Files.isDirectory(orderPath)) {
                    continue;
                }
                String orderName = orderPath.getFileName().toString();

                // Scan partName subdirectories
                try (DirectoryStream<Path> partStream = Files.newDirectoryStream(orderPath)) {
                    for (Path partPath : partStream) {
                        if (!Files.isDirectory(partPath)) {
                            continue;
                        }
                        String partName = partPath.getFileName().toString();
                        String relativePath = String.format("%s/%s", orderName, partName);

                        // Check if the directory is in use by any machine
                        if (!activePaths.contains(relativePath)) {
                            if (attemptDirectoryDeletion(partPath, queueType)) {
                                deletedDirectories++;
                            } else {
                                blockedDirectories++;
                            }
                        }
                    }
                }

                // Delete orderName directory if it is empty
                if (fileSystemService.isDirectoryAccessible(orderPath) && isDirectoryEmpty(orderPath)) {
                    try {
                        if (Files.deleteIfExists(orderPath)) {
                            auditDeletion(orderPath, queueType);
                            logger.info("Deleted empty orderName directory: {}", orderPath);
                            deletedDirectories++;
                        } else {
                            logger.warn("Failed to delete orderName directory {}, possibly already removed or inaccessible", orderPath);
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to delete orderName directory {}: {}", orderPath, e.getMessage());
                        blockedDirectories++;
                    }
                } else if (!fileSystemService.isDirectoryAccessible(orderPath)) {
                    logger.warn("OrderName directory {} is blocked and cannot be deleted", orderPath);
                    blockedDirectories++;
                }
            }
        }

        return new CleanupResult(deletedDirectories, blockedDirectories);
    }

    /**
     * Processes blocked directories stored in the database, attempting to delete them.
     *
     * @return Cleanup result (number of deleted and blocked directories)
     */
    @Transactional
    private CleanupResult processBlockedDirectories() {
        logger.info("Processing blocked directories.");
        List<BlockedDirectory> blockedDirs = blockedDirectoryRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        final int MAX_ATTEMPTS = 5;
        final int MAX_AGE_HOURS = 24;
        int deletedDirectories = 0;
        int blockedDirectories = 0;

        for (BlockedDirectory blockedDir : blockedDirs) {
            Path dirPath = Paths.get(blockedDir.getPath());
            if (!Files.exists(dirPath)) {
                blockedDirectoryRepository.delete(blockedDir);
                logger.info("Removed entry for non-existent blocked directory: {}", dirPath);
                deletedDirectories++;
                continue;
            }

            // Check if the directory is too old
            if (blockedDir.getCreatedAt().plusHours(MAX_AGE_HOURS).isBefore(now)) {
                logger.warn("Directory {} exceeded maximum retention time ({} hours). Requires administrator intervention.", dirPath, MAX_AGE_HOURS);
                blockedDirectoryRepository.delete(blockedDir);
                blockedDirectories++;
                continue;
            }

            // Attempt to delete the directory
            try {
                if (attemptDirectoryDeletion(dirPath, blockedDir.getQueueType())) {
                    blockedDirectoryRepository.delete(blockedDir);
                    logger.info("Successfully deleted previously blocked directory: {}", dirPath);
                    deletedDirectories++;
                } else {
                    blockedDir.setAttempts(blockedDir.getAttempts() + 1);
                    blockedDir.setLastAttempt(now);
                    if (blockedDir.getAttempts() >= MAX_ATTEMPTS) {
                        logger.warn("Directory {} reached maximum deletion attempts ({}). Requires administrator intervention.", dirPath, MAX_ATTEMPTS);
                        blockedDirectoryRepository.delete(blockedDir);
                        blockedDirectories++;
                    } else {
                        blockedDirectoryRepository.save(blockedDir);
                        logger.info("Retried deletion of blocked directory: {}. Attempt: {}", dirPath, blockedDir.getAttempts());
                        blockedDirectories++;
                    }
                }
            } catch (IOException e) {
                logger.error("Error during deletion attempt of blocked directory {}: {}", dirPath, e.getMessage(), e);
                blockedDirectories++;
            }
        }

        return new CleanupResult(deletedDirectories, blockedDirectories);
    }

    /**
     * Retrieves active directory paths ([orderName]/[partName]) for all machines using the specified programPath.
     *
     * @param programPath Path to the machine's program directory
     * @return Set of active paths
     */
    private Set<String> getActiveDirectoryPathsForProgramPath(String programPath) {
        // Find all machines using this programPath
        List<Machine> machines = machineRepository.findByProgramPath(programPath);
        Set<String> activePaths = new HashSet<>();

        // Collect active paths for each machine
        for (Machine machine : machines) {
            String queueType = String.valueOf(machine.getId());
            List<ProductionQueueItem> items = productionQueueItemRepository.findByQueueType(queueType);
            activePaths.addAll(items.stream()
                    .map(item -> {
                        String orderName = fileSystemService.sanitizeName(item.getOrderName(), "NoOrderName_" + item.getId());
                        String partName = fileSystemService.sanitizeName(item.getPartName(), "NoPartName_" + item.getId());
                        return String.format("%s/%s", orderName, partName);
                    })
                    .collect(Collectors.toSet()));
        }

        logger.debug("Active directory paths for programPath {}: {}", programPath, activePaths);
        return activePaths;
    }

    /**
     * Attempts to delete a directory and its contents, handling locked files and directories.
     *
     * @param dirPath   Path to the directory
     * @param queueType Machine ID
     * @return true if the directory was deleted, false if it contains locked files or is itself locked
     * @throws IOException If a filesystem operation fails
     */
    private boolean attemptDirectoryDeletion(Path dirPath, String queueType) throws IOException {
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            logger.debug("Directory {} does not exist or is not a directory", dirPath);
            return true;
        }

        boolean hasLockedFiles = false;

        // Scan files in the directory
        try (DirectoryStream<Path> fileStream = Files.newDirectoryStream(dirPath)) {
            for (Path filePath : fileStream) {
                if (Files.isRegularFile(filePath)) {
                    if (fileSystemService.isFileAccessible(filePath)) {
                        if (Files.deleteIfExists(filePath)) {
                            auditDeletion(filePath, queueType);
                            logger.debug("Deleted file: {}", filePath);
                        } else {
                            logger.warn("Failed to delete file {}, possibly already removed or inaccessible", filePath);
                            hasLockedFiles = true;
                        }
                    } else {
                        hasLockedFiles = true;
                        logger.warn("File {} is locked and cannot be deleted", filePath);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error while scanning files in directory {}: {}", dirPath, e.getMessage(), e);
            hasLockedFiles = true;
        }

        // Attempt to delete the directory if it is empty and not locked
        if (!hasLockedFiles && isDirectoryEmpty(dirPath)) {
            if (fileSystemService.isDirectoryAccessible(dirPath)) {
                if (Files.deleteIfExists(dirPath)) {
                    auditDeletion(dirPath, queueType);
                    logger.info("Successfully deleted unused directory: {}", dirPath);
                    return true;
                } else {
                    logger.warn("Failed to delete directory {}, possibly already removed or inaccessible", dirPath);
                    // Sprawdź, czy katalog nadal istnieje
                    if (Files.exists(dirPath)) {
                        logger.error("Directory {} still exists after deletion attempt", dirPath);
                        return false;
                    }
                    return true;
                }
            } else {
                logger.warn("Directory {} is locked and cannot be deleted", dirPath);
                // Register the blocked directory in the database
                BlockedDirectory blockedDir = BlockedDirectory.builder()
                        .path(dirPath.toString())
                        .queueType(queueType)
                        .attempts(1)
                        .createdAt(LocalDateTime.now())
                        .lastAttempt(LocalDateTime.now())
                        .build();
                blockedDirectoryRepository.save(blockedDir);
                logger.info("Registered blocked directory: {}. Attempt: 1", dirPath);
                return false;
            }
        } else if (hasLockedFiles) {
            // Register the blocked directory in the database
            BlockedDirectory blockedDir = BlockedDirectory.builder()
                    .path(dirPath.toString())
                    .queueType(queueType)
                    .attempts(1)
                    .createdAt(LocalDateTime.now())
                    .lastAttempt(LocalDateTime.now())
                    .build();
            blockedDirectoryRepository.save(blockedDir);
            logger.info("Registered blocked directory: {}. Attempt: 1", dirPath);
            return false;
        }

        return false;
    }

    /**
     * Checks if a directory is empty.
     *
     * @param dirPath Path to the directory
     * @return true if the directory is empty, false otherwise
     * @throws IOException If a filesystem operation fails
     */
    private boolean isDirectoryEmpty(Path dirPath) throws IOException {
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dirPath)) {
            return !dirStream.iterator().hasNext();
        }
    }

    /**
     * Logs the deletion of a file or directory for auditing purposes.
     *
     * @param path      Path to the deleted resource
     * @param queueType Machine ID
     */
    private void auditDeletion(Path path, String queueType) {
        logger.info("Audit: Deleted resource: {}, queueType: {}, time: {}", path, queueType, Instant.now());
    }

    /**
     * Sends a system notification with a summary of the cleanup.
     *
     * @param deletedDirectories Number of deleted directories
     * @param blockedDirectories Number of blocked directories
     */
    private void sendCleanupNotification(int deletedDirectories, int blockedDirectories) {
        String description;
        if (deletedDirectories == 0 && blockedDirectories == 0) {
            description = "Directory cleanup completed. No unused directories found for deletion.";
        } else {
            description = String.format(
                    "Directory cleanup completed. Deleted %d directories. %d directories were blocked and require further attention.",
                    deletedDirectories, blockedDirectories
            );
        }
        notificationService.createAndSendSystemNotification(description, NotificationDescription.DirectoryCleanupCompleted);
        logger.info("Sent system notification: {}", description);
    }

    /**
     * Class storing cleanup results.
     */
    static class CleanupResult {
        final int deletedDirectories;
        final int blockedDirectories;

        CleanupResult(int deletedDirectories, int blockedDirectories) {
            this.deletedDirectories = deletedDirectories;
            this.blockedDirectories = blockedDirectories;
        }
    }
}