package com.example.infraboxapi;

import com.example.infraboxapi.productionQueueItem.DirectoryCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO) // <-- DODAJ TĘ LINIJKĘ
public class InfraBoxApiApplication {

    private static final Logger logger = LoggerFactory.getLogger(InfraBoxApiApplication.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DirectoryCleanupService directoryCleanupService;

    // Flaga do śledzenia aktywności aplikacji
    private final AtomicBoolean isProcessingRequests = new AtomicBoolean(false);

    public static void main(String[] args) {
        SpringApplication.run(InfraBoxApiApplication.class, args);
    }

    @PostConstruct
    public void onStartup() {
        checkMountOnStartup();
    }

    /**
     * Ta metoda jest wywoływana, gdy aplikacja jest w pełni uruchomiona i gotowa do przyjmowania żądań.
     * Jest to niezawodny sposób na wykonanie logiki startowej.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Aplikacja jest w pełni uruchomiona. Wywołanie zadania czyszczenia dla wszystkich maszyn...");
        try {
            directoryCleanupService.cleanupAllMachines();
            logger.info("Zadanie czyszczenia zakończone pomyślnie.");
        } catch (Exception e) {
            logger.error("Błąd podczas wykonywania zadania czyszczenia po starcie aplikacji: {}", e.getMessage(), e);
        }
    }

    private void checkMountOnStartup() {
        String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local";
        Path mountDir;

        if ("prod".equalsIgnoreCase(appEnv)) {
            mountDir = Paths.get("/cnc");
        } else if ("docker-local".equalsIgnoreCase(appEnv)) {
            mountDir = Paths.get("/cnc");
            ensureDirectoryExists(mountDir);
        } else {
            mountDir = Paths.get("./cnc");
            ensureDirectoryExists(mountDir);
        }
        checkMountedResource(mountDir);
    }

    /**
     * Metoda do restartowania aplikacji.
     */
    private void restartApplication() {
        logger.info("Rozpoczynanie restartu aplikacji...");

        int retryCount = 0;
        int maxRetries = 12; // 12 prób co 5 sekund = 1 minuta
        while (isProcessingRequests.get() && retryCount < maxRetries) {
            logger.warn("Aplikacja jest w trakcie przetwarzania żądań/zadań. Opóźnianie restartu... (Próba {}/{})", retryCount + 1, maxRetries);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                logger.error("Przerwano opóźnienie restartu: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
            retryCount++;
        }

        if (isProcessingRequests.get()) {
            logger.error("Aplikacja nadal przetwarza żądania po {} próbach. Anulowanie restartu.", maxRetries);
            return;
        }

        // Uruchomienie zamknięcia kontekstu w nowym wątku, aby uniknąć blokowania
        Thread shutdownThread = new Thread(() -> {
            logger.info("Zamykanie kontekstu aplikacji Spring...");
            System.exit(SpringApplication.exit(applicationContext, () -> 0));
        });
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }

    /**
     * Logowanie informacji po zamknięciu kontekstu.
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        logger.info("Kontekst aplikacji został zamknięty. Serwer web powinien zostać zatrzymany automatycznie.");
    }

    // Planowanie restartu codziennie o 00:00
    @Scheduled(cron = "0 0 0 * * ?")
    public void scheduleApplicationRestart() {
        logger.info("Zaplanowany restart aplikacji o 00:00...");
        restartApplication();
    }

    public void setProcessingRequests(boolean processing) {
        isProcessingRequests.set(processing);
    }

    private void ensureDirectoryExists(Path directory) {
        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
                logger.info("Utworzono katalog {} dla trybu lokalnego lub docker-local.", directory.toString());
            } catch (IOException e) {
                logger.error("Błąd podczas tworzenia katalogu {}: {}", directory, e.getMessage());
            }
        } else if (!Files.isDirectory(directory)) {
            logger.error("{} istnieje, ale nie jest katalogiem.", directory);
        }
    }

    private void checkMountedResource(Path mountDir) {
        if (Files.exists(mountDir) && Files.isDirectory(mountDir)) {
            if (Files.isReadable(mountDir)) {
                logger.info("Zasób {} jest dostępny do odczytu.", mountDir);
                listDirectoryContents(mountDir);
            } else {
                logger.error("Zasób {} istnieje, ale brak uprawnień do odczytu.", mountDir);
            }
            if (Files.isWritable(mountDir)) {
                logger.info("Zasób {} jest dostępny do zapisu.", mountDir);
                Path testFile = mountDir.resolve("test.txt");
                try {
                    Files.writeString(testFile, "Test");
                    logger.info("Utworzono plik testowy w {} - zapis działa prawidłowo.", mountDir);
                    Files.deleteIfExists(testFile);
                    logger.info("Usunięto plik testowy z {} - usuwanie działa prawidłowo.", mountDir);
                } catch (IOException e) {
                    logger.error("Błąd podczas tworzenia/usuwania pliku testowego w {}: {}", mountDir, e.getMessage());
                }
            } else {
                logger.error("Zasób {} istnieje, ale brak uprawnień do zapisu.", mountDir);
            }
        } else {
            logger.error("Zasób {} nie jest zamontowany lub nie istnieje.", mountDir);
        }
    }

    private void listDirectoryContents(Path directory) {
        try (var contents = Files.list(directory)) {
            var files = contents.collect(Collectors.toList());
            if (!files.isEmpty()) {
                logger.info("Zawartość katalogu {}:", directory);
                for (Path file : files) {
                    String type = Files.isDirectory(file) ? "katalog" : "plik";
                    logger.info("- {} ({})", file.getFileName(), type);
                }
            } else {
                logger.info("Katalog {} jest pusty.", directory);
            }
        } catch (IOException e) {
            logger.error("Nie udało się wylistować zawartości {} - możliwy problem z uprawnieniami lub błędem I/O.", directory, e);
        }
    }
}