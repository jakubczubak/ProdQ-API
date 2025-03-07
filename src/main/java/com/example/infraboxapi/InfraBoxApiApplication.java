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
        File mountDir = new File("/mnt/cnc");

        // Sprawdzenie, czy katalog istnieje
        if (mountDir.exists()) {
            if (mountDir.isDirectory()) {
                // Sprawdzenie odczytu
                if (mountDir.canRead()) {
                    logger.info("Zasób sieciowy /mnt/cnc jest zamontowany i dostępny do odczytu.");
                    listDirectoryContents(mountDir); // Listowanie zawartości
                } else {
                    logger.error("Zasób /mnt/cnc istnieje, ale brak uprawnień do odczytu.");
                }

                // Sprawdzenie zapisu
                if (mountDir.canWrite()) {
                    logger.info("Zasób /mnt/cnc jest dostępny do zapisu.");
                    // Testowy zapis pliku
                    File testFile = new File(mountDir, "test.txt");
                    try {
                        if (testFile.createNewFile()) {
                            logger.info("Utworzono plik testowy w /mnt/cnc - zapis działa prawidłowo.");
                        }
                    } catch (IOException e) {
                        logger.error("Błąd podczas tworzenia pliku testowego w /mnt/cnc: " + e.getMessage());
                    }
                } else {
                    logger.error("Zasób /mnt/cnc istnieje, ale brak uprawnień do zapisu.");
                }

                // Sprawdzenie usuwania (jeśli plik testowy został utworzony)
                if (mountDir.canWrite()) {
                    File testFile = new File(mountDir, "test.txt");
                    if (testFile.exists()) {
                        if (testFile.delete()) {
                            logger.info("Usunięto plik testowy z /mnt/cnc - usuwanie działa prawidłowo.");
                        } else {
                            logger.error("Nie udało się usunąć pliku testowego z /mnt/cnc - brak uprawnień lub inny problem.");
                        }
                    }
                } else {
                    logger.error("Zasób /mnt/cnc istnieje, ale brak uprawnień do usuwania (na podstawie canWrite).");
                }
            } else {
                logger.error("/mnt/cnc istnieje, ale nie jest katalogiem.");
            }
        } else {
            logger.error("Zasób sieciowy /mnt/cnc nie jest zamontowany lub nie istnieje.");
        }
    }

    private void listDirectoryContents(File directory) {
        File[] contents = directory.listFiles();
        if (contents != null) {
            if (contents.length > 0) {
                logger.info("Zawartość katalogu /mnt/cnc:");
                for (File file : contents) {
                    String type = file.isDirectory() ? "katalog" : "plik";
                    logger.info("- {} ({})", file.getName(), type);
                }
            } else {
                logger.info("Katalog /mnt/cnc jest pusty.");
            }
        } else {
            logger.error("Nie udało się wylistować zawartości /mnt/cnc -可能ny problem z uprawnieniami lub błędem I/O.");
        }
    }
}