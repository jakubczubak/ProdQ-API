package com.example.prodqapi.productionQueueItem;

import com.example.prodqapi.FileProductionItem.ProductionFileInfo;
import com.example.prodqapi.FileProductionItem.ProductionFileInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FileWatcherService {

    private static final Logger logger = LoggerFactory.getLogger(FileWatcherService.class);

    private final MachineRepository machineRepository;
    private final ProductionQueueItemRepository productionQueueItemRepository;
    private final ProductionFileInfoService productionFileInfoService;

    public FileWatcherService(
            MachineRepository machineRepository,
            ProductionQueueItemRepository productionQueueItemRepository,
            ProductionFileInfoService productionFileInfoService) {
        this.machineRepository = machineRepository;
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.productionFileInfoService = productionFileInfoService;
    }

    @Transactional
    public void checkQueueFile(String queueType) {
        logger.info("Sprawdzanie pliku kolejki dla queueType: {}", queueType);
        try {
            if (queueType == null || "ncQueue".equals(queueType) || "completed".equals(queueType)) {
                logger.debug("Pominięto sprawdzanie dla queueType: {}", queueType);
                return;
            }

            Integer machineId = Integer.parseInt(queueType);
            Optional<Machine> machineOpt = machineRepository.findById(machineId);
            if (machineOpt.isEmpty()) {
                logger.warn("Nie znaleziono maszyny dla queueType: {}", queueType);
                return;
            }

            Machine machine = machineOpt.get();
            Path queueFile = Paths.get(machine.getQueueFilePath(), machine.getMachineName() + ".txt").toAbsolutePath().normalize();

            if (!Files.exists(queueFile) || !Files.isReadable(queueFile)) {
                logger.warn("Plik kolejki {} nie istnieje lub jest nieodczytywalny", queueFile);
                return;
            }

            handleFileChange(queueFile);

        } catch (NumberFormatException e) {
            logger.warn("Nieprawidłowy format queueType: {}", queueType, e);
        } catch (Exception e) {
            logger.error("Błąd podczas sprawdzania pliku kolejki dla queueType {}: {}", queueType, e.getMessage(), e);
        }
    }

    @Transactional
    protected void handleFileChange(Path filePath) throws IOException {
        logger.info("Przetwarzanie pliku: {}", filePath);
        List<String> lines = Files.readAllLines(filePath);

        Pattern idPattern = Pattern.compile("^\\s*ID Programu\\s*:\\s*(\\d+)");
        Pattern filePattern = Pattern.compile("^\\d+\\.\\s+(.+?)\\s*\\|\\s*\\[(OK|NOK)]");

        Integer currentProgramId = null;

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            Matcher idMatcher = idPattern.matcher(line);
            if (idMatcher.find()) {
                currentProgramId = Integer.parseInt(idMatcher.group(1));
                logger.debug("Znaleziono ID programu w nagłówku: {}", currentProgramId);
                continue;
            }

            if (line.trim().equals("---")) {
                currentProgramId = null;
                continue;
            }

            if (currentProgramId == null) {
                continue;
            }

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
            logger.warn("Nie znaleziono programu o ID {} w bazie danych", programId);
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
                logger.info("Zaktualizowano status pliku {} dla programu {} na {}", fileName, programId, isCompleted ? "OK" : "NOK");

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
            logger.warn("Nie znaleziono pliku {} dla programu {} w bazie danych", fileName, programId);
        }
    }
}