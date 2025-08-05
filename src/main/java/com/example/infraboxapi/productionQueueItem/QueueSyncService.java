package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
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

    private final Map<String, FileTime> lastModifiedTimes = new HashMap<>();

    public QueueSyncService(
            MachineRepository machineRepository,
            ProductionQueueItemRepository productionQueueItemRepository,
            ProductionFileInfoService productionFileInfoService,
            NotificationService notificationService,
            MachineQueueFileGeneratorService machineQueueFileGeneratorService) {
        this.machineRepository = machineRepository;
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.productionFileInfoService = productionFileInfoService;
        this.notificationService = notificationService;
        this.machineQueueFileGeneratorService = machineQueueFileGeneratorService;
    }

    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void syncAllQueues() {
        logger.debug("Started periodic synchronization of queues for all machines");
        List<Machine> machines = machineRepository.findAll();

        for (Machine machine : machines) {
            try {
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
            updateAttachmentStatuses(filePath);
        }

        String newContent = machineQueueFileGeneratorService.generateQueueFileForMachine(queueType);

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

                program.setCompleted(allMpfCompleted);
                productionQueueItemRepository.save(program);
                logger.info("Updated program {} status to completed={}", programId, allMpfCompleted);
            }
        } else {
            logger.warn("File {} for program {} not found in database", fileName, programId);
        }
    }
}