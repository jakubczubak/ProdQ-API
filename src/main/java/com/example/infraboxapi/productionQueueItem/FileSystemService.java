package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Serwis odpowiedzialny za operacje na systemie plików, w tym tworzenie katalogów, zapisywanie i usuwanie plików.
 */
@Service
public class FileSystemService {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemService.class);

    private final ProductionQueueItemRepository productionQueueItemRepository;

    public FileSystemService(ProductionQueueItemRepository productionQueueItemRepository) {
        this.productionQueueItemRepository = productionQueueItemRepository;
    }

    /**
     * Synchronizuje załączniki z katalogiem maszyny, nadpisując istniejące pliki i usuwając nieużywane.
     * Jeśli plik jest zablokowany, zapisuje go pod unikalną nazwą z sufiksem.
     * Używa tego samego katalogu dla identycznych orderName i partName.
     *
     * @param programPath ścieżka programowa maszyny
     * @param orderName nazwa zamówienia
     * @param partName nazwa części
     * @param files lista załączników do zsynchronizowania
     * @throws IOException w przypadku błędu operacji na pliku
     */
    public void synchronizeFiles(String programPath, String orderName, String partName, List<ProductionFileInfo> files) throws IOException {
        Path basePath = createDirectoryStructure(programPath, orderName, partName);

        // Pobierz wszystkie programy z tym samym orderName i partName w bazie danych
        Set<String> allAppFiles = productionQueueItemRepository.findByOrderNameAndPartName(orderName, partName)
                .stream()
                .flatMap(item -> item.getFiles() != null ? item.getFiles().stream() : Collections.<ProductionFileInfo>emptyList().stream())
                .map(ProductionFileInfo::getFileName)
                .collect(Collectors.toSet());

        // Pobierz listę istniejących plików na dysku
        Set<String> existingFiles = Files.exists(basePath)
                ? Files.list(basePath)
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet())
                : Collections.emptySet();

        // Pobierz listę plików dla bieżącego elementu
        Set<String> currentAppFiles = files == null
                ? Collections.emptySet()
                : files.stream()
                .map(ProductionFileInfo::getFileName)
                .collect(Collectors.toSet());

        // Usuń pliki z dysku, które nie znajdują się w liście załączników aplikacji (dla wszystkich programów z tym samym orderName i partName)
        for (String diskFile : existingFiles) {
            if (!allAppFiles.contains(diskFile)) {
                Path filePath = basePath.resolve(diskFile);
                if (isFileAccessible(filePath)) {
                    Files.deleteIfExists(filePath);
                    logger.info("Usunięto niepotrzebny plik: {}, czas: {}", filePath, Instant.now());
                } else {
                    logger.warn("Nie można usunąć pliku {}, ponieważ jest zablokowany", filePath);
                }
            }
        }

        // Zapisz pliki z aplikacji na dysk, nadpisując istniejące lub tworząc z sufiksem w przypadku blokady
        if (files != null && !files.isEmpty()) {
            for (ProductionFileInfo file : files) {
                String fileName = file.getFileName();
                Path filePath = basePath.resolve(fileName);

                // Sprawdź, czy plik istnieje i jest zablokowany
                if (Files.exists(filePath) && !isFileAccessible(filePath)) {
                    logger.warn("Plik {} jest zablokowany, zapisuję pod unikalną nazwą", filePath);
                    filePath = getUniqueFilePath(basePath, fileName);
                }

                // Zapisz plik, nadpisując istniejący, jeśli nie jest zablokowany
                Path tempFile = basePath.resolve(fileName + ".tmp");
                Files.write(tempFile, file.getFileContent());
                Files.move(tempFile, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Zapisano plik: {}, rozmiar: {} bajtów, czas: {}", filePath, file.getFileSize(), Instant.now());
            }
        }
    }

    /**
     * Tworzy strukturę katalogów dla podanych nazw zamówienia i części, używając zawsze oryginalnej nazwy partName.
     *
     * @param programPath ścieżka programowa maszyny
     * @param orderName nazwa zamówienia
     * @param partName nazwa części
     * @return ścieżka do katalogu części
     * @throws IOException w przypadku błędu operacji na pliku
     */
    private Path createDirectoryStructure(String programPath, String orderName, String partName) throws IOException {
        Path basePath = Paths.get(programPath, orderName, partName);
        if (Files.exists(basePath) && !Files.isDirectory(basePath)) {
            logger.warn("Ścieżka {} istnieje jako plik, nie można utworzyć katalogu", basePath);
            throw new IOException("Ścieżka istnieje jako plik: " + basePath);
        }

        Files.createDirectories(basePath);
        logger.debug("Utworzono lub użyto istniejącej struktury katalogów: {}", basePath);
        return basePath;
    }

    /**
     * Generuje unikalną ścieżkę dla pliku, dodając numer wersji (_v2, _v3, itd.) w razie konfliktu nazw.
     *
     * @param basePath katalog bazowy
     * @param fileName nazwa pliku
     * @return unikalna ścieżka do pliku
     * @throws IOException w przypadku braku możliwości znalezienia unikalnej nazwy
     */
    private Path getUniqueFilePath(Path basePath, String fileName) throws IOException {
        Path filePath = basePath.resolve(fileName);
        if (!Files.exists(filePath)) {
            return filePath;
        }
        String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        String ext = fileName.substring(fileName.lastIndexOf("."));
        int version = 2;
        while (true) {
            String versionedName = String.format("%s_v%d%s", nameWithoutExt, version, ext);
            filePath = basePath.resolve(versionedName);
            if (!Files.exists(filePath)) {
                return filePath;
            }
            version++;
            if (version > 1000) {
                throw new IOException("Nie można znaleźć unikalnej nazwy dla pliku: " + fileName);
            }
        }
    }

    /**
     * Sprawdza, czy plik jest dostępny (niezablokowany) do usuwania lub nadpisywania.
     *
     * @param filePath ścieżka do pliku
     * @return true, jeśli plik jest dostępny, false w przeciwnym razie
     */
    private boolean isFileAccessible(Path filePath) {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return true;
        }
        try {
            Files.newOutputStream(filePath, StandardOpenOption.WRITE, StandardOpenOption.APPEND).close();
            return true;
        } catch (IOException e) {
            logger.warn("Plik {} jest niedostępny: {}", filePath, e.getMessage());
            return false;
        }
    }
}