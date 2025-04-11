package com.example.infraboxapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;

@SpringBootApplication
public class InfraBoxApiApplication {

    private static final Logger logger = LoggerFactory.getLogger(InfraBoxApiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(InfraBoxApiApplication.class, args);
    }

    @PostConstruct
    public void checkMountOnStartup() {
        String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local"; // Domyślnie local
        File mountDir;

        if ("prod".equalsIgnoreCase(appEnv)) {
            // Tryb produkcyjny: sprawdzanie zamontowanego zasobu /mnt/cnc
            mountDir = new File("/mnt/cnc");
        } else if ("docker-local".equalsIgnoreCase(appEnv)) {
            // Tryb docker-local: używamy /cnc w kontenerze
            mountDir = new File("/cnc");
            ensureDirectoryExists(mountDir); // Tworzenie katalogu, jeśli nie istnieje
        } else {
            // Tryb lokalny: używamy ./cnc w katalogu projektu
            mountDir = new File("./cnc");
            ensureDirectoryExists(mountDir); // Tworzenie katalogu, jeśli nie istnieje
        }

        checkMountedResource(mountDir);
    }

    private void ensureDirectoryExists(File directory) {
        if (!directory.exists()) {
            try {
                if (directory.mkdirs()) {
                    logger.info("Utworzono katalog {} dla trybu lokalnego lub docker-local.", directory.getAbsolutePath());
                } else {
                    logger.error("Nie udało się utworzyć katalogu {}.", directory.getAbsolutePath());
                }
            } catch (SecurityException e) {
                logger.error("Błąd uprawnień podczas tworzenia katalogu {}: {}", directory.getAbsolutePath(), e.getMessage());
            }
        } else if (!directory.isDirectory()) {
            logger.error("{} istnieje, ale nie jest katalogiem.", directory.getAbsolutePath());
        }
    }

    private void checkMountedResource(File mountDir) {
        if (mountDir.exists() && mountDir.isDirectory()) {
            // Sprawdzenie odczytu
            if (mountDir.canRead()) {
                logger.info("Zasób {} jest dostępny do odczytu.", mountDir.getAbsolutePath());
                listDirectoryContents(mountDir);
            } else {
                logger.error("Zasób {} istnieje, ale brak uprawnień do odczytu.", mountDir.getAbsolutePath());
            }

            // Sprawdzenie zapisu
            if (mountDir.canWrite()) {
                logger.info("Zasób {} jest dostępny do zapisu.", mountDir.getAbsolutePath());
                File testFile = new File(mountDir, "test.txt");
                try {
                    if (testFile.createNewFile()) {
                        logger.info("Utworzono plik testowy w {} - zapis działa prawidłowo.", mountDir.getAbsolutePath());
                    }
                } catch (IOException e) {
                    logger.error("Błąd podczas tworzenia pliku testowego w {}: {}", mountDir.getAbsolutePath(), e.getMessage());
                }

                // Sprawdzenie usuwania
                if (testFile.exists()) {
                    if (testFile.delete()) {
                        logger.info("Usunięto plik testowy z {} - usuwanie działa prawidłowo.", mountDir.getAbsolutePath());
                    } else {
                        logger.error("Nie udało się usunąć pliku testowego z {} - brak uprawnień lub inny problem.", mountDir.getAbsolutePath());
                    }
                }
            } else {
                logger.error("Zasób {} istnieje, ale brak uprawnień do zapisu.", mountDir.getAbsolutePath());
            }
        } else {
            logger.error("Zasób {} nie jest zamontowany lub nie istnieje.", mountDir.getAbsolutePath());
        }
    }

    private void listDirectoryContents(File directory) {
        File[] contents = directory.listFiles();
        if (contents != null) {
            if (contents.length > 0) {
                logger.info("Zawartość katalogu {}:", directory.getAbsolutePath());
                for (File file : contents) {
                    String type = file.isDirectory() ? "katalog" : "plik";
                    logger.info("- {} ({})", file.getName(), type);
                }
            } else {
                logger.info("Katalog {} jest pusty.", directory.getAbsolutePath());
            }
        } else {
            logger.error("Nie udało się wylistować zawartości {} - możliwy problem z uprawnieniami lub błędem I/O.", directory.getAbsolutePath());
        }
    }
}