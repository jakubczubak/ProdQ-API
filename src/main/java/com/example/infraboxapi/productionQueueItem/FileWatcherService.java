package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serwis odpowiedzialny za sprawdzanie plików kolejki i aktualizację statusów załączników oraz programów w odpowiedzi na operacje użytkownika.
 */
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

    /**
     * Sprawdza plik kolejki dla maszyny o podanym queueType i aktualizuje statusy załączników oraz programów.
     *
     * @param queueType ID maszyny (jako String)
     */
    @Transactional
    public void checkQueueFile(String queueType) {
        logger.info("Sprawdzanie pliku kolejki dla queueType: {}", queueType);
        try {
            // Pomiń dla ncQueue i completed
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

            if (!Files.exists(queueFile)) {
                logger.debug("Plik kolejki {} nie istnieje", queueFile);
                return;
            }
            if (!Files.isReadable(queueFile)) {
                logger.error("Brak uprawnień do odczytu pliku kolejki: {}", queueFile);
                return;
            }

            logger.info("Przetwarzanie pliku kolejki: {}", queueFile);
            handleFileChange(queueFile, machine);

        } catch (NumberFormatException e) {
            logger.warn("Nieprawidłowy format queueType: {}", queueType);
        } catch (Exception e) {
            logger.error("Błąd podczas sprawdzania pliku kolejki dla queueType {}: {}", queueType, e.getMessage(), e);
        }
    }

    /**
     * Obsługuje zmianę pliku kolejki, aktualizując statusy załączników i programów.
     *
     * @param filePath ścieżka do pliku kolejki
     * @param machine  maszyna powiązana z plikiem
     */
    @Transactional
    protected void handleFileChange(Path filePath, Machine machine) {
        logger.info("Przetwarzanie pliku: {}", filePath);
        try {
            List<String> lines;
            try {
                lines = Files.readAllLines(filePath);
                logger.debug("Odczytano {} linii z pliku {}", lines.size(), filePath);
            } catch (IOException e) {
                logger.error("Nie udało się odczytać pliku {}: {}", filePath, e.getMessage(), e);
                return;
            }

            // Parser: pozycja./orderName/partName/fileName - ilość szt. id: ID | [UKONCZONE|NIEUKONCZONE]
            Pattern pattern = Pattern.compile(
                    "^(\\d+)\\./([^/]+)/([^/]+)/([^\\s]+?\\.mpf(?:\\.mpf)?)\\s*-\\s*(\\d+)\\s*szt\\.\\s*id:\\s*(\\d+)\\s*\\|\\s*\\[(UKONCZONE|NIEUKONCZONE)]",
                    Pattern.CASE_INSENSITIVE
            );

            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.equals("---") ||
                        trimmedLine.startsWith("/**") || trimmedLine.startsWith("Program:") || trimmedLine.equals("*/")) {
                    logger.debug("Pominięto linię: {}", trimmedLine);
                    continue;
                }

                logger.debug("Parsowanie linii: {}", trimmedLine);
                Matcher matcher = pattern.matcher(trimmedLine);
                if (matcher.matches()) {
                    String fileName = matcher.group(4);
                    String partName = matcher.group(3);
                    Integer programId = Integer.parseInt(matcher.group(6));
                    boolean isCompleted = "UKONCZONE".equalsIgnoreCase(matcher.group(7));

                    logger.debug("Rozpoznano: fileName={}, partName={}, programId={}, status={}",
                            fileName, partName, programId, isCompleted ? "UKONCZONE" : "NIEUKONCZONE");

                    Optional<ProductionQueueItem> programOpt = productionQueueItemRepository.findByIdWithFiles(programId);
                    if (programOpt.isPresent()) {
                        ProductionQueueItem program = programOpt.get();
                        // Wyszukaj plik z uwzględnieniem partName dla większej precyzji
                        Optional<ProductionFileInfo> fileInfoOpt = program.getFiles().stream()
                                .filter(f -> f.getFileName().equalsIgnoreCase(fileName) &&
                                        program.getPartName().equalsIgnoreCase(partName))
                                .findFirst();

                        if (fileInfoOpt.isPresent()) {
                            ProductionFileInfo fileInfo = fileInfoOpt.get();
                            logger.debug("Baza: fileName={}, completed={}, Plik: status={}",
                                    fileInfo.getFileName(), fileInfo.isCompleted(), isCompleted ? "UKONCZONE" : "NIEUKONCZONE");
                            if (fileInfo.isCompleted() != isCompleted) {
                                fileInfo.setCompleted(isCompleted);
                                productionFileInfoService.save(fileInfo);
                                logger.info("Zaktualizowano status pliku {} dla programu {} na {}", fileName, programId, isCompleted ? "UKONCZONE" : "NIEUKONCZONE");

                                // Sprawdź, czy wszystkie załączniki .MPF są ukończone
                                boolean allMpfCompleted = program.getFiles().stream()
                                        .filter(f -> f.getFileName().toLowerCase().endsWith(".mpf"))
                                        .allMatch(ProductionFileInfo::isCompleted);

                                program.setCompleted(allMpfCompleted);
                                productionQueueItemRepository.save(program);
                                logger.info("Zaktualizowano status programu {} na completed={}", programId, allMpfCompleted);
                            } else {
                                logger.debug("Status pliku {} dla programu {} nie wymaga aktualizacji (baza: {}, plik: {})",
                                        fileName, programId, fileInfo.isCompleted(), isCompleted);
                            }
                        } else {
                            logger.warn("Nie znaleziono pliku {} dla programu {} z partName {} w bazie danych",
                                    fileName, programId, partName);
                        }
                    } else {
                        logger.warn("Nie znaleziono programu o ID {} w bazie danych", programId);
                    }
                } else {
                    logger.warn("Niepoprawny format linii w pliku {}: '{}'", filePath, trimmedLine);
                }
            }
        } catch (Exception e) {
            logger.error("Nieoczekiwany błąd podczas przetwarzania pliku {}: {}", filePath, e.getMessage(), e);
        }
    }

    /**
     * Sanitizuje nazwę, usuwając polskie znaki i niedozwolone znaki.
     *
     * @param name nazwa do sanitizacji
     * @return sanitizowana nazwa
     */
    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }
        // Normalizuj znaki i usuń diakrytyki
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
}