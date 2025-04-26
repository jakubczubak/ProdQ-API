package com.example.infraboxapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class InfraBoxApiApplication {

    private static final Logger logger = LoggerFactory.getLogger(InfraBoxApiApplication.class);

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