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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
        logger.info("Inicjalizacja FileWatcherService...");
        new Thread(() -> {
            try {
                watchQueueFiles();
            } catch (Exception e) {
                logger.error("Wątek FileWatcherService zakończył się nieoczekiwanie: {}", e.getMessage(), e);
                throw e;
            }
        }, "FileWatcherThread").start();
    }

    private void watchQueueFiles() {
        WatchService watchService = null;
        Set<Path> registeredDirs = new HashSet<>();
        int lastMachineCount = -1;

        try {
            watchService = FileSystems.getDefault().newWatchService();
            logger.info("Utworzono WatchService");

            while (true) {
                // Pobierz maszyny
                List<Machine> machines = machineRepository.findAll();
                int currentMachineCount = machines.size();

                if (currentMachineCount != lastMachineCount) {
                    logger.info("Zmiana liczby maszyn: {} (poprzednio: {})", currentMachineCount, lastMachineCount);
                    lastMachineCount = currentMachineCount;
                }

                logger.debug("Znaleziono {} maszyn do monitorowania", currentMachineCount);
                for (Machine machine : machines) {
                    logger.debug("Maszyna: {}, oczekiwany plik: {}/{}.txt", machine.getMachineName(), machine.getQueueFilePath(), machine.getMachineName());
                }

                // Rejestruj katalogi maszyn
                for (Machine machine : machines) {
                    Path queueDir;
                    try {
                        queueDir = Paths.get(machine.getQueueFilePath()).toAbsolutePath().normalize();
                    } catch (Exception e) {
                        logger.error("Nieprawidłowa ścieżka queueFilePath dla maszyny {}: {}", machine.getMachineName(), machine.getQueueFilePath(), e);
                        continue;
                    }

                    Path queueFile = queueDir.resolve(machine.getMachineName() + ".txt");
                    if (!Files.exists(queueDir)) {
                        logger.warn("Katalog kolejki {} dla maszyny {} nie istnieje", queueDir, machine.getMachineName());
                        continue;
                    }
                    if (!Files.isDirectory(queueDir)) {
                        logger.warn("Ścieżka {} dla maszyny {} nie jest katalogiem", queueDir, machine.getMachineName());
                        continue;
                    }
                    if (!Files.isReadable(queueDir)) {
                        logger.error("Brak uprawnień do odczytu katalogu {} dla maszyny {}", queueDir, machine.getMachineName());
                        continue;
                    }

                    if (!registeredDirs.contains(queueDir)) {
                        try {
                            queueDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                            registeredDirs.add(queueDir);
                            logger.info("Zarejestrowano katalog kolejki: {} dla maszyny {} (plik: {})", queueDir, machine.getMachineName(), queueFile);
                            // Spróbuj odczytać istniejący plik kolejki
                            if (Files.exists(queueFile) && Files.isReadable(queueFile)) {
                                logger.info("Wykryto istniejący plik kolejki: {}, synchronizuję statusy", queueFile);
                                handleFileChange(queueFile);
                            } else {
                                logger.debug("Plik kolejki {} jeszcze nie istnieje", queueFile);
                            }
                        } catch (IOException e) {
                            logger.error("Nie udało się zarejestrować katalogu {} dla maszyny {}: {}", queueDir, machine.getMachineName(), e.getMessage(), e);
                        }
                    }
                }

                // Sprawdź zdarzenia (nieblokująco)
                logger.debug("Sprawdzanie zdarzeń WatchService...");
                WatchKey key;
                while ((key = watchService.poll(1000, TimeUnit.MILLISECONDS)) != null) {
                    Path watchablePath = (Path) key.watchable();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changedFile = watchablePath.resolve((Path) event.context());
                        // Ignoruj pliki tymczasowe
                        String fileName = changedFile.getFileName().toString();
                        if (fileName.endsWith("~") || fileName.endsWith(".tmp")) {
                            logger.debug("Zignorowano plik tymczasowy: {}", changedFile);
                            continue;
                        }
                        if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            logger.info("Wykryto zmianę w pliku: {}", changedFile);
                            handleFileChange(changedFile);
                        } else {
                            logger.debug("Zignorowano zdarzenie typu: {} dla pliku: {}", event.kind(), event.context());
                        }
                    }
                    boolean valid = key.reset();
                    if (!valid) {
                        logger.warn("Klucz WatchService dla {} stał się nieprawidłowy", watchablePath);
                        registeredDirs.remove(watchablePath);
                    }
                }

                // Krótka przerwa
                Thread.sleep(1000); // 1s na odświeżanie listy maszyn
            }
        } catch (IOException e) {
            logger.error("Błąd podczas inicjalizacji WatchService: {}", e.getMessage(), e);
            throw new RuntimeException("Błąd podczas obserwacji plików kolejki: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.error("Przerwano pętlę WatchService: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        } finally {
            if (watchService != null) {
                try {
                    watchService.close();
                    logger.info("Zamknięto WatchService");
                } catch (IOException e) {
                    logger.error("Błąd podczas zamykania WatchService: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Obsługuje zmianę pliku kolejki, aktualizując statusy załączników i programów.
     *
     * @param filePath ścieżka do zmienionego pliku
     */
    @Transactional
    private void handleFileChange(Path filePath) {
        logger.info("Przetwarzanie zmiany pliku: {}", filePath);
        try {
            Optional<Machine> machineOpt = machineRepository.findAll().stream()
                    .filter(m -> {
                        Path expectedPath = Paths.get(m.getQueueFilePath(), m.getMachineName() + ".txt").toAbsolutePath().normalize();
                        return expectedPath.toString().equalsIgnoreCase(filePath.toAbsolutePath().normalize().toString());
                    })
                    .findFirst();

            if (machineOpt.isEmpty()) {
                logger.warn("Nie znaleziono maszyny dla pliku: {}. Oczekiwane pliki:", filePath);
                machineRepository.findAll().forEach(m ->
                        logger.warn(" - Maszyna: {}, plik: {}/{}.txt", m.getMachineName(), m.getQueueFilePath(), m.getMachineName()));
                return;
            }

            Machine machine = machineOpt.get();
            logger.info("Plik {} powiązany z maszyną: {}", filePath, machine.getMachineName());
            String queueType = String.valueOf(machine.getId());

            List<String> lines;
            try {
                lines = Files.readAllLines(filePath);
            } catch (IOException e) {
                logger.error("Nie udało się przeczytać pliku {}: {}", filePath, e.getMessage(), e);
                return;
            }
            logger.debug("Przeczytano {} linii z pliku {}", lines.size(), filePath);

            // Parser: pozycja. /orderName/partName/fileName - ilość szt. id: ID | [UKONCZONE|NIEUKONCZONE]
            Pattern pattern = Pattern.compile(
                    "^(\\d+)\\s*\\.\\s*/([^/]+)/([^/]+)/([^\\s]+?\\.mpf(?:\\.mpf)?)\\s*-\\s*(\\d+)\\s*szt\\.\\s*id:\\s*(\\d+)\\s*\\|\\s*\\[(UKONCZONE|NIEUKONCZONE)]",
                    Pattern.CASE_INSENSITIVE
            );

            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.equals("---")) {
                    logger.debug("Pominięto linię: {}", trimmedLine);
                    continue;
                }

                logger.debug("Parsowanie linii: {}", trimmedLine);
                Matcher matcher = pattern.matcher(trimmedLine);
                if (matcher.matches()) {
                    String fileName = matcher.group(4);
                    Integer programId = Integer.parseInt(matcher.group(6));
                    boolean isCompleted = "UKONCZONE".equalsIgnoreCase(matcher.group(7));

                    logger.debug("Rozpoznano: fileName={}, programId={}, status={}", fileName, programId, isCompleted ? "UKONCZONE" : "NIEUKONCZONE");

                    Optional<ProductionQueueItem> programOpt = productionQueueItemRepository.findByIdWithFiles(programId);
                    if (programOpt.isPresent()) {
                        ProductionQueueItem program = programOpt.get();
                        Optional<ProductionFileInfo> fileInfoOpt = program.getFiles().stream()
                                .filter(f -> f.getFileName().equalsIgnoreCase(fileName))
                                .findFirst();

                        if (fileInfoOpt.isPresent()) {
                            ProductionFileInfo fileInfo = fileInfoOpt.get();
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
                                logger.debug("Status pliku {} dla programu {} nie wymaga aktualizacji", fileName, programId);
                            }
                        } else {
                            logger.warn("Nie znaleziono pliku {} dla programu {} w bazie danych", fileName, programId);
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