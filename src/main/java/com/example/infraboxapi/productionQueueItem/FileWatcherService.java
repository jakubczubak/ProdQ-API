package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serwis odpowiedzialny za obserwowanie zmian w plikach kolejki i aktualizację statusów załączników oraz programów.
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
     * Inicjalizuje obserwację plików kolejki dla wszystkich maszyn.
     */
    @PostConstruct
    public void startWatching() {
        new Thread(this::watchQueueFiles).start();
    }

    private void watchQueueFiles() {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            List<Machine> machines = machineRepository.findAll();

            for (Machine machine : machines) {
                Path queueDir = Paths.get(machine.getQueueFilePath());
                if (Files.exists(queueDir) && Files.isDirectory(queueDir)) {
                    queueDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                } else {
                    logger.warn("Katalog kolejki {} nie istnieje lub nie jest katalogiem", queueDir);
                }
            }

            while (true) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        Path changedFile = ((Path) key.watchable()).resolve((Path) event.context());
                        handleFileChange(changedFile);
                    }
                }
                key.reset();
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Błąd podczas obserwacji plików kolejki", e);
            throw new RuntimeException("Błąd podczas obserwacji plików kolejki: " + e.getMessage(), e);
        }
    }

    /**
     * Obsługuje zmianę pliku kolejki, aktualizując statusy załączników i programów.
     *
     * @param filePath ścieżka do zmienionego pliku
     */
    @Transactional
    private void handleFileChange(Path filePath) {
        try {
            Optional<Machine> machineOpt = machineRepository.findAll().stream()
                    .filter(m -> filePath.equals(Paths.get(m.getQueueFilePath(), m.getMachineName() + ".txt")))
                    .findFirst();

            if (machineOpt.isEmpty()) {
                logger.warn("Nie znaleziono maszyny dla pliku {}", filePath);
                return;
            }

            Machine machine = machineOpt.get();
            String queueType = String.valueOf(machine.getId());
            List<String> lines = Files.readAllLines(filePath);
            // Parser: pozycja. orderName/partName/fileName - ilość szt. [- dodatkowe info] id: ID [Ukonczone|Nieukonczone]
            Pattern pattern = Pattern.compile(
                    "^(\\d+)\\.\\s+([^/]+)/([^/]+)/([^\\s]+)\\s+-\\s+(\\d+)\\s+szt\\.(?:\\s+-\\s+([^\\[]+))?\\s+id:\\s+(\\d+)\\s+\\[(Ukonczone|Nieukonczone)]",
                    Pattern.CASE_INSENSITIVE
            );

            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.equals("---")) {
                    continue; // Pomiń puste linie, komentarze i separatory
                }

                Matcher matcher = pattern.matcher(trimmedLine);
                if (matcher.matches()) {
                    String fileName = sanitizeFileName(matcher.group(4));
                    Integer programId = Integer.parseInt(matcher.group(7));
                    boolean isCompleted = "Ukonczone".equalsIgnoreCase(matcher.group(8));

                    Optional<ProductionQueueItem> programOpt = productionQueueItemRepository.findById(programId);
                    if (programOpt.isPresent()) {
                        ProductionQueueItem program = programOpt.get();
                        Optional<ProductionFileInfo> fileInfoOpt = program.getFiles().stream()
                                .filter(f -> sanitizeFileName(f.getFileName()).equals(fileName) && fileName.toLowerCase().endsWith(".mpf"))
                                .findFirst();

                        if (fileInfoOpt.isPresent()) {
                            ProductionFileInfo fileInfo = fileInfoOpt.get();
                            if (fileInfo.isCompleted() != isCompleted) {
                                fileInfo.setCompleted(isCompleted);
                                productionFileInfoService.save(fileInfo);
                                logger.info("Zaktualizowano status pliku {} dla programu {} na {}", fileName, programId, isCompleted ? "Ukonczone" : "Nieukonczone");

                                // Sprawdź, czy wszystkie załączniki .MPF są ukończone
                                boolean allMpfCompleted = program.getFiles().stream()
                                        .filter(f -> f.getFileName().toLowerCase().endsWith(".mpf"))
                                        .allMatch(ProductionFileInfo::isCompleted);

                                program.setCompleted(allMpfCompleted);
                                productionQueueItemRepository.save(program);
                                logger.info("Zaktualizowano status programu {} na completed={}", programId, allMpfCompleted);
                            }
                        } else {
                            logger.warn("Nie znaleziono pliku {} dla programu {} w bazie danych", fileName, programId);
                        }
                    } else {
                        logger.warn("Nie znaleziono programu o ID {} w bazie danych", programId);
                    }
                } else {
                    logger.warn("Niepoprawny format linii w pliku {}: {}", filePath, line);
                }
            }
        } catch (IOException e) {
            logger.error("Błąd podczas przetwarzania pliku kolejki {}", filePath, e);
            throw new RuntimeException("Błąd podczas przetwarzania pliku kolejki: " + e.getMessage(), e);
        }
    }

    /**
     * Sanitizuje nazwę, usuwając polskie znaki i niedozwolone znaki.
     *
     * @param name nazwa do sanitizacji
     * @return sanitized nazwa
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