package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable; // Dodany import
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service responsible for periodically synchronizing machine queue files with database data.
 * Updates attachment statuses based on the text file and generates a new queue file only if necessary.
 * Uses asynchronous processing, modification time checks, and notifications for errors.
 */
@Service
public class QueueSyncService {

    private static final Logger logger = LoggerFactory.getLogger(QueueSyncService.class);

    private final MachineRepository machineRepository;
    private final ProductionQueueItemRepository productionQueueItemRepository;
    private final ProductionFileInfoService productionFileInfoService;
    private final FileSystemService fileSystemService;
    private final NotificationService notificationService;

    private final Map<String, FileTime> lastModifiedTimes = new HashMap<>();
    private final Map<String, LocalDateTime> lastSyncTimes = new HashMap<>();

    public QueueSyncService(
            MachineRepository machineRepository,
            ProductionQueueItemRepository productionQueueItemRepository,
            ProductionFileInfoService productionFileInfoService,
            FileSystemService fileSystemService,
            NotificationService notificationService) {
        this.machineRepository = machineRepository;
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.productionFileInfoService = productionFileInfoService;
        this.fileSystemService = fileSystemService;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    @Transactional
    public void syncAllQueues() {
        logger.debug("Started periodic synchronization of queues for all machines");
        List<Machine> machines = machineRepository.findAll();

        for (Machine machine : machines) {
            String queueType = String.valueOf(machine.getId());
            try {
                syncQueueForMachine(queueType, machine);
            } catch (IOException e) {
                String errorMessage = String.format("Queue synchronization failed for machine %s (ID: %s): %s",
                        machine.getMachineName(), queueType, e.getMessage());
                logger.error(errorMessage, e);
                notificationService.createAndSendSystemNotification(errorMessage, NotificationDescription.QueueSyncFailed);
            }
        }

        logger.debug("Completed periodic synchronization of queues");
    }

    @Async("queueSyncExecutor")
    @Transactional
    public void syncQueueForMachine(String queueType, Machine machine) throws IOException {
        logger.debug("Synchronizing queue for queueType: {}, machine: {}", queueType, machine.getMachineName());

        if ("ncQueue".equals(queueType) || "completed".equals(queueType)) {
            logger.debug("Skipped synchronization for queueType: {}", queueType);
            return;
        }

        String fileName = machine.getMachineName() + ".txt";
        String cleanedPath = machine.getQueueFilePath().replaceFirst("^/+", "").replaceFirst("^cnc/?", "");
        String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local";
        Path mountDir = "prod".equalsIgnoreCase(appEnv) || "docker-local".equalsIgnoreCase(appEnv)
                ? Paths.get("/cnc")
                : Paths.get("./cnc");
        Path resolvedPath = cleanedPath.isEmpty() ? mountDir : mountDir.resolve(cleanedPath).normalize();
        Path filePath = resolvedPath.resolve(fileName);

        boolean fileModified = isFileModified(filePath, queueType);
        if (fileModified) {
            updateAttachmentStatuses(filePath, machine, queueType);
        } else {
            logger.debug("Queue file {} has not been modified since last synchronization", filePath);
        }

        LocalDateTime lastSyncTime = lastSyncTimes.getOrDefault(queueType, LocalDateTime.of(1970, 1, 1, 0, 0));

        // --- TUTAJ JEST PIERWSZA POPRAWKA ---
        List<ProductionQueueItem> programs = productionQueueItemRepository.findByQueueType(queueType, Pageable.unpaged()).getContent()
                .stream()
                .filter(p -> p.getLastModified() == null || p.getLastModified().isAfter(lastSyncTime))
                .sorted(Comparator.comparing(ProductionQueueItem::getOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        if (programs.isEmpty() && !fileModified) {
            logger.debug("No modified programs or file changes for queueType: {}. Skipping synchronization.", queueType);
            return;
        }

        // --- TUTAJ JEST DRUGA POPRAWKA ---
        List<ProductionQueueItem> allPrograms = productionQueueItemRepository.findByQueueType(queueType, Pageable.unpaged()).getContent()
                .stream()
                .sorted(Comparator.comparing(ProductionQueueItem::getOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        String newContent = generateQueueContent(allPrograms, machine);

        String existingContent = "";
        if (Files.exists(filePath) && Files.isReadable(filePath)) {
            try {
                existingContent = Files.readString(filePath);
            } catch (IOException e) {
                logger.warn("Failed to read queue file {}: {}", filePath, e.getMessage());
            }
        }

        if (!contentEqualsIgnoreTimestamp(newContent, existingContent)) {
            logger.debug("Detected changes in queue content for machine {}. Updating file: {}", machine.getMachineName(), filePath);
            try {
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, newContent);
                logger.debug("Updated queue file: {}", filePath);
                lastModifiedTimes.put(queueType, Files.getLastModifiedTime(filePath));
            } catch (IOException e) {
                String errorMessage = String.format("Failed to write queue file %s for machine %s: %s",
                        filePath, machine.getMachineName(), e.getMessage());
                logger.error(errorMessage, e);
                notificationService.createAndSendSystemNotification(errorMessage, NotificationDescription.QueueSyncFailed);
                throw e;
            }

            for (ProductionQueueItem program : programs) {
                String orderName = fileSystemService.sanitizeName(program.getOrderName(), "NoOrderName_" + program.getId());
                String partName = program.getPartName();
                try {
                    fileSystemService.synchronizeFiles(machine.getProgramPath(), orderName, partName, program.getFiles());
                    logger.debug("Synchronized attachments for program ID: {}, orderName: {}, partName: {}",
                            program.getId(), orderName, partName);
                } catch (IOException e) {
                    String errorMessage = String.format("Failed to synchronize attachments for program ID: %d, machine: %s: %s",
                            program.getId(), machine.getMachineName(), e.getMessage());
                    logger.error(errorMessage, e);
                    notificationService.createAndSendSystemNotification(errorMessage, NotificationDescription.QueueSyncFailed);
                }
            }
        } else {
            logger.debug("No changes in queue content for machine {}. File {} remains unchanged.", machine.getMachineName(), filePath);
        }

        lastSyncTimes.put(queueType, LocalDateTime.now());
    }

    private boolean contentEqualsIgnoreTimestamp(String newContent, String existingContent) {
        if (newContent.equals(existingContent)) {
            return true;
        }

        String[] newLines = newContent.split("\n");
        String[] existingLines = existingContent.split("\n");

        if (newLines.length != existingLines.length) {
            return false;
        }

        for (int i = 0; i < newLines.length; i++) {
            if (newLines[i].startsWith("# Wygenerowano:") && existingLines[i].startsWith("# Wygenerowano:")) {
                continue;
            }
            if (!newLines[i].equals(existingLines[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean isFileModified(Path filePath, String queueType) {
        if (!Files.exists(filePath)) {
            logger.debug("File {} does not exist, treated as modified", filePath);
            return true;
        }

        try {
            FileTime currentModifiedTime = Files.getLastModifiedTime(filePath);
            FileTime lastKnownModifiedTime = lastModifiedTimes.get(queueType);

            if (lastKnownModifiedTime == null || currentModifiedTime.compareTo(lastKnownModifiedTime) > 0) {
                logger.debug("File {} was modified (current: {}, last: {})",
                        filePath, currentModifiedTime, lastKnownModifiedTime);
                return true;
            }
            return false;
        } catch (IOException e) {
            logger.warn("Error checking modification time of file {}: {}", filePath, e.getMessage());
            return true;
        }
    }

    @Transactional
    private void updateAttachmentStatuses(Path filePath, Machine machine, String queueType) {
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            logger.debug("Queue file {} does not exist or is not readable", filePath);
            return;
        }

        logger.debug("Updating attachment statuses based on file: {}", filePath);
        try {
            List<String> lines = Files.readAllLines(filePath);
            logger.debug("Read {} lines from file {}", lines.size(), filePath);

            Pattern pattern = Pattern.compile(
                    "^(\\d+)\\./([^/]+)/([^/]+)/([^\\s]+?\\.[mM][pP][fF](?:\\.[mM][pP][fF])?)\\s*id:\\s*(\\d+)\\s*\\|\\s*\\[(OK|NOK)]",
                    Pattern.CASE_INSENSITIVE
            );

            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.equals("---") ||
                        trimmedLine.startsWith("/**") || trimmedLine.startsWith("Program:") || trimmedLine.equals("*/") ||
                        trimmedLine.startsWith("Autor:") || trimmedLine.startsWith("Ilość:") || trimmedLine.startsWith("Uwagi:") ||
                        trimmedLine.startsWith("Zamówienie:") || trimmedLine.startsWith("Nazwa elementu:") || trimmedLine.startsWith("Przygotówka:")) {
                    logger.debug("Skipped line: {}", trimmedLine);
                    continue;
                }


                logger.debug("Parsing line: {}", trimmedLine);
                Matcher matcher = pattern.matcher(trimmedLine);
                if (matcher.matches()) {
                    String fileName = matcher.group(4);
                    String partName = matcher.group(3);
                    Integer programId = Integer.parseInt(matcher.group(5));
                    boolean isCompleted = "OK".equalsIgnoreCase(matcher.group(6));

                    logger.debug("Parsed: fileName={}, partName={}, programId={}, status={}",
                            fileName, partName, programId, isCompleted ? "OK" : "NOK");

                    Optional<ProductionQueueItem> programOpt = productionQueueItemRepository.findByIdWithFiles(programId);
                    if (programOpt.isPresent()) {
                        ProductionQueueItem program = programOpt.get();
                        Optional<ProductionFileInfo> fileInfoOpt = program.getFiles().stream()
                                .filter(f -> f.getFileName().equalsIgnoreCase(fileName) &&
                                        program.getPartName().equalsIgnoreCase(partName))
                                .findFirst();

                        if (fileInfoOpt.isPresent()) {
                            ProductionFileInfo fileInfo = fileInfoOpt.get();
                            logger.debug("Database: fileName={}, completed={}, File: status={}",
                                    fileInfo.getFileName(), fileInfo.isCompleted(), isCompleted ? "OK" : "NOK");
                            if (fileInfo.isCompleted() != isCompleted) {
                                fileInfo.setCompleted(isCompleted);
                                productionFileInfoService.save(fileInfo);
                                logger.info("Updated status of file {} for program {} to {}", fileName, programId, isCompleted ? "OK" : "NOK");

                                boolean allMpfCompleted = program.getFiles().stream()
                                        .filter(f -> f.getFileName().toLowerCase().endsWith(".mpf"))
                                        .allMatch(ProductionFileInfo::isCompleted);

                                program.setCompleted(allMpfCompleted);
                                program.setLastModified(LocalDateTime.now());
                                productionQueueItemRepository.save(program);
                                logger.info("Updated program {} status to completed={}", programId, allMpfCompleted);
                            } else {
                                logger.debug("Status of file {} for program {} does not require update", fileName, programId);
                            }
                        } else {
                            logger.warn("File {} for program {} with partName {} not found in database",
                                    fileName, programId, partName);
                        }
                    } else {
                        logger.warn("Program with ID {} not found in database", programId);
                    }
                } else {
                    logger.warn("Invalid line format in file {}: '{}'", filePath, trimmedLine);
                }
            }
        } catch (IOException e) {
            logger.error("Error reading queue file {}: {}", filePath, e.getMessage(), e);
            notificationService.createAndSendSystemNotification(
                    String.format("Failed to read queue file %s for machine %s: %s",
                            filePath, machine.getMachineName(), e.getMessage()),
                    NotificationDescription.QueueSyncFailed
            );
        } catch (Exception e) {
            logger.error("Unexpected error updating statuses for file {}: {}", filePath, e.getMessage(), e);
            notificationService.createAndSendSystemNotification(
                    String.format("Unexpected error updating statuses for file %s, machine %s: %s",
                            filePath, machine.getMachineName(), e.getMessage()),
                    NotificationDescription.QueueSyncFailed
            );
        }
    }

    private String generateQueueContent(List<ProductionQueueItem> programs, Machine machine) {
        StringBuilder content = new StringBuilder();
        content.append("# Edytuj tylko statusy w nawiasach: [OK] lub [NOK].\n");
        content.append("# Przykład: zmień '[NOK]' na '[OK]'. Nie zmieniaj ID, nazw ani innych danych!\n");
        content.append("# Ścieżka /orderName/partName/załącznik wskazuje lokalizację programu.\n");
        content.append("# Błędy w formacie linii mogą zostać zignorowane przez system.\n");
        content.append("# Wygenerowano: 2023-01-01 00:00:00\n\n");

        if (programs.isEmpty()) {
            content.append("# Brak programów w kolejce dla tej maszyny.\n");
            return content.toString();
        }

        int position = 1;
        String lastPartName = null;
        Integer lastProgramId = null;

        for (ProductionQueueItem program : programs) {
            List<ProductionFileInfo> mpfFiles = program.getFiles() != null ?
                    program.getFiles().stream()
                            .filter(file -> file.getFileName().toLowerCase().endsWith(".mpf"))
                            .sorted(Comparator.comparing(ProductionFileInfo::getOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                            .collect(Collectors.toList()) :
                    List.of();

            if (!mpfFiles.isEmpty()) {
                // Wersje "surowe" dla nagłówka
                String rawOrderName = program.getOrderName() != null ? program.getOrderName() : "";
                String rawPartName = program.getPartName() != null ? program.getPartName() : "NoPartName_" + program.getId();
                String additionalInfo = program.getAdditionalInfo() != null ? program.getAdditionalInfo() : "";
                String author = program.getAuthor() != null ? program.getAuthor() : "";

                // Wersje "oczyszczone" dla ścieżek
                String sanitizedOrderName = sanitizeFileName(program.getOrderName(), "NoOrderName_" + program.getId());
                String sanitizedPartName = sanitizeFileName(program.getPartName(), "NoPartName_" + program.getId());

                int quantity = program.getQuantity();

                if (lastProgramId != null && !lastProgramId.equals(program.getId())) {
                    content.append("\n---\n\n");
                }

                content.append("/**\n");
                content.append(String.format("Zamówienie: %s\n", rawOrderName));
                content.append(String.format("Nazwa elementu: %s\n", rawPartName));
                if (!author.isEmpty()) {
                    content.append(String.format("Autor: %s\n", author));
                }
                content.append(String.format("Ilość: %d szt\n", quantity));
                if (!additionalInfo.isEmpty()) {
                    content.append(wrapCommentWithPrefix(additionalInfo, "Uwagi: "));
                }
                content.append(" */\n");
                content.append("\n");

                if (lastPartName != null && !rawPartName.equals(lastPartName) && lastProgramId != null && lastProgramId.equals(program.getId())) {
                    content.append("\n");
                }

                for (ProductionFileInfo mpfFile : mpfFiles) {
                    boolean isFileCompleted = mpfFile.isCompleted();
                    String status = isFileCompleted ? "[OK]" : "[NOK]";
                    String mpfFileName = sanitizeFileName(mpfFile.getFileName(), "NoFileName_" + mpfFile.getId());
                    String entry = String.format("%d./%s/%s/%s id: %d | %s\n",
                            position++,
                            sanitizedOrderName,
                            sanitizedPartName,
                            mpfFileName,
                            program.getId(),
                            status);
                    content.append(entry);
                }

                lastPartName = rawPartName;
                lastProgramId = program.getId();
            }
        }

        return content.toString();
    }

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

    private String wrapCommentWithPrefix(String comment, String prefix) {
        final int MAX_LINE_LENGTH = 80;
        StringBuilder wrapped = new StringBuilder();
        String[] words = comment.split("\\s+");
        StringBuilder currentLine = new StringBuilder(prefix);
        boolean firstLine = true;

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > MAX_LINE_LENGTH) {
                wrapped.append(currentLine.toString().trim()).append("\n");
                currentLine = new StringBuilder(firstLine ? prefix : "");
                firstLine = false;
            }
            currentLine.append(word).append(" ");
        }

        if (currentLine.length() > (firstLine ? prefix.length() : 0)) {
            wrapped.append(currentLine.toString().trim()).append("\n");
        }

        return wrapped.toString();
    }

    public MachineRepository getMachineRepository() {
        return machineRepository;
    }
}