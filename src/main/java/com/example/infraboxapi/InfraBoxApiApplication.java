package com.example.infraboxapi;

import com.example.infraboxapi.productionQueueItem.DirectoryCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class InfraBoxApiApplication {

    private static final Logger logger = LoggerFactory.getLogger(InfraBoxApiApplication.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ServletWebServerApplicationContext webServerContext;

    @Autowired
    private DirectoryCleanupService directoryCleanupService;

    // Flaga do śledzenia aktywności aplikacji
    private final AtomicBoolean isProcessingRequests = new AtomicBoolean(false);

    public static void main(String[] args) {
        SpringApplication.run(InfraBoxApiApplication.class, args);
    }

    @PostConstruct
    public void checkMountOnStartup() {
        String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local"; // Domyślnie local
        Path mountDir;

        if ("prod".equalsIgnoreCase(appEnv)) {
            // Tryb produkcyjny: sprawdzanie zamontowanego zasobu /cnc
            mountDir = Paths.get("/cnc");
        } else if ("docker-local".equalsIgnoreCase(appEnv)) {
            // Tryb docker-local: używamy /cnc w kontenerze
            mountDir = Paths.get("/cnc");
            ensureDirectoryExists(mountDir); // Tworzenie katalogu, jeśli nie istnieje
        } else {
            // Tryb lokalny: używamy ./cnc w katalogu projektu
            mountDir = Paths.get("./cnc");
            ensureDirectoryExists(mountDir); // Tworzenie katalogu, jeśli nie istnieje
        }

        checkMountedResource(mountDir);

        // Wykonaj czyszczenie katalogów po każdym starcie aplikacji
        logger.info("Wykonywanie zadania czyszczenia katalogów po starcie aplikacji...");
        triggerCleanupAfterStartup();
    }

    // Metoda do restartowania aplikacji
    private void restartApplication() {
        logger.info("Rozpoczynanie restartu aplikacji o 00:00...");

        // Sprawdź, czy aplikacja jest w trakcie obsługi żądań lub zadań
        int retryCount = 0;
        int maxRetries = 12; // 12 prób co 5 sekund = 1 minuta
        while (isProcessingRequests.get() && retryCount < maxRetries) {
            logger.warn("Aplikacja jest w trakcie przetwarzania żądań/zadań. Opóźnianie restartu... (Próba {}/{})", retryCount + 1, maxRetries);
            try {
                Thread.sleep(5000); // Poczekaj 5 sekund
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

        try {
            // Zamknij bieżący kontekst aplikacji
            logger.info("Zamykanie kontekstu aplikacji...");
            if (applicationContext instanceof ConfigurableApplicationContext) {
                ((ConfigurableApplicationContext) applicationContext).close();
            } else {
                logger.warn("ApplicationContext nie jest typu ConfigurableApplicationContext – nie można wywołać close().");
            }

            // Zatrzymaj serwer HTTP
            logger.info("Zatrzymywanie serwera HTTP...");
            webServerContext.getWebServer().stop();

            // Poczekaj chwilę, aby upewnić się, że wszystko zostało zamknięte
            Thread.sleep(5000);

            // Zakończ proces Java - Docker automatycznie zrestartuje kontener
            logger.info("Zakańczanie procesu Java, aby zwolnić uchwyty...");
            System.exit(0);
        } catch (Exception e) {
            logger.error("Błąd podczas restartowania aplikacji: {}", e.getMessage(), e);
            // W przypadku błędu również wywołaj System.exit, aby upewnić się, że proces się zakończy
            System.exit(1);
        }
    }

    // Planowanie restartu codziennie o 00:00
    @Scheduled(cron = "0 0 0 * * ?") // Codziennie o 00:00
    public void scheduleApplicationRestart() {
        logger.info("Zaplanowany restart aplikacji o 00:00...");
        restartApplication();
    }

    // Wywołanie zadania czyszczenia po starcie aplikacji
    private void triggerCleanupAfterStartup() {
        try {
            // Poczekaj chwilę, aby upewnić się, że aplikacja jest w pełni uruchomiona
            Thread.sleep(10000); // 10 sekund opóźnienia
            logger.info("Wywołanie zadania czyszczenia dla wszystkich maszyn...");
            directoryCleanupService.cleanupAllMachines();
            logger.info("Zadanie czyszczenia zakończone pomyślnie.");
        } catch (InterruptedException e) {
            logger.error("Przerwano opóźnienie przed czyszczeniem: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Błąd podczas wykonywania zadania czyszczenia: {}", e.getMessage(), e);
        }
    }

    // Metoda do oznaczania, że aplikacja przetwarza żądania/zadania
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
            // Sprawdzenie odczytu
            if (Files.isReadable(mountDir)) {
                logger.info("Zasób {} jest dostępny do odczytu.", mountDir);
                listDirectoryContents(mountDir);
            } else {
                logger.error("Zasób {} istnieje, ale brak uprawnień do odczytu.", mountDir);
            }

            // Sprawdzenie zapisu
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