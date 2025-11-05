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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QueueSyncService {

    private static final Logger logger = LoggerFactory.getLogger(QueueSyncService.class);

    private final MachineRepository machineRepository;
    private final ProductionQueueItemRepository productionQueueItemRepository;
    private final ProductionFileInfoService productionFileInfoService;
    private final NotificationService notificationService;
    private final MachineQueueFileGeneratorService machineQueueFileGeneratorService;
    private final FileSystemService fileSystemService; // Nowa zależność

    private final Map<String, FileTime> lastModifiedTimes = new HashMap<>();

    public QueueSyncService(
            MachineRepository machineRepository,
            ProductionQueueItemRepository productionQueueItemRepository,
            ProductionFileInfoService productionFileInfoService,
            NotificationService notificationService,
            MachineQueueFileGeneratorService machineQueueFileGeneratorService,
            FileSystemService fileSystemService) { // Nowy parametr w konstruktorze
        this.machineRepository = machineRepository;
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.productionFileInfoService = productionFileInfoService;
        this.notificationService = notificationService;
        this.machineQueueFileGeneratorService = machineQueueFileGeneratorService;
        this.fileSystemService = fileSystemService; // Przypisanie nowej zależności
    }

    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void syncAllQueues() {
        logger.debug("Started periodic synchronization of queues for all machines");
        List<Machine> machines = machineRepository.findAll();

        for (Machine machine : machines) {
            try {
                // ==========================================================
                // === POCZĄTEK NOWEJ LOGIKI - AUTOMATYCZNA SYNCHRONIZACJA PLIKÓW .MPF ===
                // ==========================================================

                logger.debug("Starting automatic file sync for machine: {}", machine.getMachineName());

                // 1. Pobierz wszystkie aktywne programy dla tej maszyny
                // IMPORTANT: Use findByQueueTypeWithFilesAndMaterial() to eagerly load files
                List<ProductionQueueItem> programs = productionQueueItemRepository
                        .findByQueueTypeWithFilesAndMaterial(String.valueOf(machine.getId()));

                // 2. Dla każdego programu uruchom synchronizację jego plików
                for (ProductionQueueItem program : programs) {
                    String orderName = fileSystemService.sanitizeName(program.getOrderName(), "NoOrderName_" + program.getId());
                    String partName = fileSystemService.sanitizeName(program.getPartName(), "NoPartName_" + program.getId());

                    // Ta metoda porówna, usunie, nadpisze lub doda pliki
                    fileSystemService.synchronizeFiles(machine.getProgramPath(), orderName, partName, program.getFiles());
                }

                logger.debug("Finished automatic file sync for machine: {}", machine.getMachineName());

                // ========================================================
                // === KONIEC NOWEJ LOGIKI - AUTOMATYCZNA SYNCHRONIZACJA PLIKÓW .MPF ===
                // ========================================================

                // Istniejąca logika synchronizacji pliku kolejki .txt
                syncQueueForMachine(String.valueOf(machine.getId()), machine);

            } catch (IOException e) {
                String errorMessage = String.format("Queue synchronization failed for machine %s (ID: %s): %s",
                        machine.getMachineName(), machine.getId(), e.getMessage());
                logger.error(errorMessage, e);
                notificationService.createAndSendSystemNotification(errorMessage, NotificationDescription.QueueSyncFailed);
            }
        }
        logger.debug("Completed periodic synchronization of queues");
    }

    @Async("queueSyncExecutor")
    @Transactional
    public void syncQueueForMachine(String queueType, Machine machine) throws IOException {
        if ("ncQueue".equals(queueType) || "completed".equals(queueType)) {
            return;
        }

        Path filePath = resolveQueueFilePath(machine);

        if (isFileModified(filePath, queueType)) {
            if (Files.exists(filePath)) {
                updateAttachmentStatuses(filePath);
            }
        }

        String newContent = machineQueueFileGeneratorService.generateQueueFileForMachine(queueType);

        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, newContent);
        lastModifiedTimes.put(queueType, Files.getLastModifiedTime(filePath));
    }

    private Path resolveQueueFilePath(Machine machine) {
        String fileName = machine.getMachineName() + ".txt";
        String cleanedPath = machine.getQueueFilePath().replaceFirst("^/+", "").replaceFirst("^cnc/?", "");
        String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local";
        Path mountDir = "prod".equalsIgnoreCase(appEnv) || "docker-local".equalsIgnoreCase(appEnv)
                ? Paths.get("/cnc")
                : Paths.get("./cnc");

        Path basePath = cleanedPath.isEmpty() ? mountDir : mountDir.resolve(cleanedPath).normalize();
        return basePath.resolve(fileName);
    }

    private boolean isFileModified(Path filePath, String queueType) {
        if (!Files.exists(filePath)) return true;
        try {
            FileTime currentModifiedTime = Files.getLastModifiedTime(filePath);
            FileTime lastKnownModifiedTime = lastModifiedTimes.get(queueType);
            return lastKnownModifiedTime == null || currentModifiedTime.compareTo(lastKnownModifiedTime) > 0;
        } catch (IOException e) {
            logger.warn("Error checking modification time of file {}: {}", filePath, e.getMessage());
            return true;
        }
    }

    @Transactional
    private void updateAttachmentStatuses(Path filePath) throws IOException {
        logger.debug("Updating attachment statuses based on file: {}", filePath);
        List<String> lines = Files.readAllLines(filePath);

        Pattern idPattern = Pattern.compile("^\\s*ID Programu\\s*:\\s*(\\d+)");
        Pattern filePattern = Pattern.compile("^\\d+\\.\\s+(.+?)\\s*\\|\\s*\\[(OK|NOK)]");

        Integer currentProgramId = null;

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            Matcher idMatcher = idPattern.matcher(line);
            if (idMatcher.find()) {
                currentProgramId = Integer.parseInt(idMatcher.group(1));
                continue;
            }

            if (line.trim().equals("---")) {
                currentProgramId = null;
                continue;
            }

            if (currentProgramId == null) continue;

            Matcher fileMatcher = filePattern.matcher(line);
            if (fileMatcher.matches()) {
                String fileName = fileMatcher.group(1).trim();
                boolean isCompleted = "OK".equalsIgnoreCase(fileMatcher.group(2));
                updateFileStatus(currentProgramId, fileName, isCompleted);
            }
        }
    }

    private void updateFileStatus(int programId, String fileName, boolean isCompleted) {
        Optional<ProductionQueueItem> programOpt = productionQueueItemRepository.findByIdWithFiles(programId);
        if (programOpt.isEmpty()) {
            logger.warn("Program with ID {} not found in database", programId);
            return;
        }

        ProductionQueueItem program = programOpt.get();
        Optional<ProductionFileInfo> fileInfoOpt = program.getFiles().stream()
                .filter(f -> f.getFileName().equalsIgnoreCase(fileName))
                .findFirst();

        if (fileInfoOpt.isPresent()) {
            ProductionFileInfo fileInfo = fileInfoOpt.get();
            if (fileInfo.isCompleted() != isCompleted) {
                fileInfo.setCompleted(isCompleted);
                productionFileInfoService.save(fileInfo);
                logger.info("Updated status of file {} for program {} to {}", fileName, programId, isCompleted ? "OK" : "NOK");

                boolean allMpfCompleted = program.getFiles().stream()
                        .filter(f -> f.getFileName().toLowerCase().endsWith(".mpf"))
                        .allMatch(ProductionFileInfo::isCompleted);

                // NEW LOGIC: Set operator reported flag instead of directly completing
                // This allows manual approval in UI before consuming material
                if (allMpfCompleted && !program.isCompleted()) {
                    // Operator reports completion by marking all files as OK
                    if (!Boolean.TRUE.equals(program.getOperatorReportedComplete())) {
                        program.setOperatorReportedComplete(true);
                        program.setOperatorReportedAt(java.time.LocalDateTime.now());
                        logger.info("Operator reported completion for program {} at {}",
                            programId, program.getOperatorReportedAt());
                    }
                    // DO NOT set program.completed = true here
                    // DO NOT consume material here
                } else if (!allMpfCompleted && Boolean.TRUE.equals(program.getOperatorReportedComplete())) {
                    // Operator changed mind - clear the flag
                    program.setOperatorReportedComplete(false);
                    program.setOperatorReportedAt(null);
                    logger.info("Operator cleared completion report for program {}", programId);
                }

                productionQueueItemRepository.save(program);
                logger.info("Updated program {} operator report status: reported={}",
                    programId, program.getOperatorReportedComplete());
            }
        } else {
            logger.warn("File {} for program {} not found in database", fileName, programId);
        }
    }
}