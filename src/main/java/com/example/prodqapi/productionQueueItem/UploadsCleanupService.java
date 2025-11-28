package com.example.prodqapi.productionQueueItem;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

@Service
public class UploadsCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(UploadsCleanupService.class);

    @Value("${file.upload-dir:Uploads}")
    private String uploadDir;

    private final ProductionQueueItemRepository productionQueueItemRepository;

    public UploadsCleanupService(ProductionQueueItemRepository productionQueueItemRepository) {
        this.productionQueueItemRepository = productionQueueItemRepository;
    }

    // ==========================================================
    // === POCZĄTEK NOWEGO KODU - URUCHAMIANIE PRZY STARCIE APLIKACJI ===
    // ==========================================================

    /**
     * Uruchamia jednorazowe sprzątanie w osobnym wątku zaraz po starcie aplikacji.
     * Adnotacja @PostConstruct zapewnia, że metoda zostanie wywołana po zainicjowaniu serwisu.
     * Adnotacja @Async sprawia, że wykonanie nie blokuje procesu startu aplikacji.
     */
    @PostConstruct
    @Async
    public void runCleanupOnStartup() {
        logger.info("Running initial uploads directory cleanup on application startup...");
        cleanupOrphanedUploadsDirectories();
    }

    // ========================================================
    // === KONIEC NOWEGO KODU - URUCHAMIANIE PRZY STARCIE APLIKACJI ===
    // ========================================================


    /**
     * Uruchamia się cyklicznie (codziennie o 2:00 w nocy), aby wyszukać i usunąć osierocone
     * katalogi w folderze Uploads. Osierocony katalog to taki, którego nazwa (ID)
     * nie odpowiada żadnemu istniejącemu zleceniu produkcyjnemu w bazie danych.
     */
    @Scheduled(cron = "0 0 2 * * ?") // Uruchamiaj codziennie o 2 w nocy
    @Transactional(readOnly = true)
    public void cleanupOrphanedUploadsDirectories() {
        logger.info("Starting scheduled cleanup of orphaned uploads directories...");
        Path rootDir = Paths.get(uploadDir);

        if (!Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
            logger.warn("Uploads directory '{}' does not exist or is not a directory. Cleanup skipped.", uploadDir);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDir)) {
            for (Path itemIdDir : stream) {
                // Interesują nas tylko katalogi
                if (!Files.isDirectory(itemIdDir)) {
                    continue;
                }

                try {
                    // Nazwa katalogu powinna być numerycznym ID
                    Integer itemId = Integer.parseInt(itemIdDir.getFileName().toString());

                    // Sprawdzamy, czy zlecenie o tym ID istnieje w bazie
                    if (!productionQueueItemRepository.existsById(itemId)) {
                        logger.info("Found orphaned directory for deleted item ID: {}. Deleting...", itemId);
                        deleteDirectoryRecursively(itemIdDir);
                        logger.info("Successfully deleted directory: {}", itemIdDir);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Skipping non-numeric directory found in uploads: {}", itemIdDir.getFileName());
                } catch (IOException e) {
                    logger.error("Error during deletion of directory {}: {}", itemIdDir, e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to scan uploads directory for cleanup. Error: {}", e.getMessage());
        }

        logger.info("Finished cleanup of uploads directories.");
    }

    /**
     * Rekursywnie usuwa katalog wraz z całą jego zawartością.
     * @param path Ścieżka do katalogu do usunięcia.
     * @throws IOException w przypadku problemów z operacjami na plikach.
     */
    private void deleteDirectoryRecursively(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            logger.error("Failed to delete path {}: {}", p, e.getMessage(), e);
                        }
                    });
        }
    }
}