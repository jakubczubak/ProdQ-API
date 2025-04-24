package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.text.Normalizer;
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

        long existingFilesCount = Files.exists(basePath)
                ? Files.list(basePath).filter(Files::isRegularFile).count()
                : 0;
        logger.debug("Przed synchronizacją: {} plików w katalogu {}", existingFilesCount, basePath);

        Set<String> allAppFiles = productionQueueItemRepository.findFileNamesByOrderNameAndPartName(orderName, partName);
        Set<String> existingFiles = Files.exists(basePath)
                ? Files.list(basePath)
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet())
                : Collections.emptySet();
        Set<String> currentAppFiles = files == null
                ? Collections.emptySet()
                : files.stream()
                .map(ProductionFileInfo::getFileName)
                .collect(Collectors.toSet());

        Path tempDir = Paths.get(basePath.toString(), ".temp_" + System.currentTimeMillis());
        try {
            if (!Files.isWritable(basePath)) {
                logger.error("Katalog {} nie jest zapisywalny", basePath);
                throw new IOException("Brak uprawnień do zapisu w katalogu: " + basePath);
            }
            Files.createDirectories(tempDir);
            logger.trace("Utworzono tymczasowy katalog: {}", tempDir);

            if (files != null && !files.isEmpty()) {
                for (ProductionFileInfo file : files) {
                    String fileName = file.getFileName();
                    validateAttachment(file);
                    Path tempFilePath = tempDir.resolve(fileName);

                    byte[] content = file.getFileContent();
                    if (content == null || content.length == 0) {
                        logger.error("Zawartość pliku {} jest pusta lub null", fileName);
                        throw new IllegalArgumentException("Pusta zawartość pliku: " + fileName);
                    }

                    try {
                        logger.debug("Próba zapisu pliku tymczasowego: {}, rozmiar: {} bajtów", tempFilePath, content.length);
                        Files.write(tempFilePath, content);
                        logger.debug("Zapisano tymczasowy plik: {}", tempFilePath);
                    } catch (IOException e) {
                        logger.error("Błąd podczas zapisu pliku tymczasowego {}: {}", tempFilePath, e.getMessage());
                        throw new IOException("Nie udało się zapisać pliku tymczasowego: " + tempFilePath, e);
                    }
                }
            }

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

            if (files != null && !files.isEmpty()) {
                for (ProductionFileInfo file : files) {
                    String fileName = file.getFileName();
                    Path tempFilePath = tempDir.resolve(fileName);
                    Path filePath = basePath.resolve(fileName);

                    if (!Files.exists(tempFilePath)) {
                        logger.error("Plik tymczasowy {} nie istnieje", tempFilePath);
                        throw new IOException("Plik tymczasowy nie istnieje: " + tempFilePath);
                    }

                    if (Files.exists(filePath)) {
                        if (!isFileAccessible(filePath)) {
                            logger.warn("Plik {} jest zablokowany, zapisuję pod unikalną nazwą", filePath);
                            filePath = getUniqueFilePath(basePath, fileName);
                        } else {
                            logger.info("Plik {} już istnieje, zostanie nadpisany", filePath);
                        }
                    }

                    try {
                        logger.debug("Próba przeniesienia pliku z {} do {}", tempFilePath, filePath);
                        Files.move(tempFilePath, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                        logger.info("Zapisano plik: {}, rozmiar: {} bajtów, czas: {}", filePath, file.getFileSize(), Instant.now());
                    } catch (IOException e) {
                        logger.error("Błąd podczas przenoszenia pliku z {} do {}: {}", tempFilePath, filePath, e.getMessage());
                        throw new IOException("Nie udało się przenieść pliku: " + tempFilePath + " -> " + filePath, e);
                    }
                }
            }

            long finalFilesCount = Files.exists(basePath)
                    ? Files.list(basePath).filter(Files::isRegularFile).count()
                    : 0;
            logger.debug("Po synchronizacji: {} plików w katalogu {}", finalFilesCount, basePath);

        } finally {
            if (Files.exists(tempDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
                    for (Path file : stream) {
                        Files.deleteIfExists(file);
                    }
                    Files.deleteIfExists(tempDir);
                    logger.trace("Usunięto tymczasowy katalog: {}", tempDir);
                } catch (IOException e) {
                    logger.warn("Nie udało się usunąć tymczasowego katalogu {}: {}", tempDir, e.getMessage());
                }
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
        try {
            Files.createDirectories(basePath);
            setDirectoryPermissions(basePath);
            logger.debug("Utworzono lub użyto istniejącej struktury katalogów: {}", basePath);
            return basePath;
        } catch (FileAlreadyExistsException e) {
            if (!Files.isDirectory(basePath)) {
                logger.error("Ścieżka {} istnieje jako plik, nie można utworzyć katalogu", basePath);
                throw new IOException("Ścieżka istnieje jako plik: " + basePath, e);
            }
            logger.debug("Katalog {} już istnieje", basePath);
            return basePath;
        } catch (IOException e) {
            logger.error("Błąd podczas tworzenia katalogu {}: {}", basePath, e.getMessage());
            throw new IOException("Nie udało się utworzyć katalogu: " + basePath, e);
        }
    }

    /**
     * Ustawia uprawnienia katalogu, aby był dostępny dla wszystkich użytkowników (Windows ACL).
     *
     * @param path ścieżka do katalogu
     * @throws IOException w przypadku błędu ustawiania uprawnień
     */
    private void setDirectoryPermissions(Path path) throws IOException {
        try {
            java.nio.file.attribute.AclFileAttributeView aclAttr = Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView.class);
            if (aclAttr != null) {
                java.nio.file.attribute.UserPrincipal everyone = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("Everyone");
                java.nio.file.attribute.AclEntry entry = java.nio.file.attribute.AclEntry.newBuilder()
                        .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                        .setPrincipal(everyone)
                        .setPermissions(
                                java.nio.file.attribute.AclEntryPermission.READ_DATA,
                                java.nio.file.attribute.AclEntryPermission.WRITE_DATA,
                                java.nio.file.attribute.AclEntryPermission.DELETE,
                                java.nio.file.attribute.AclEntryPermission.READ_ATTRIBUTES,
                                java.nio.file.attribute.AclEntryPermission.WRITE_ATTRIBUTES
                        )
                        .build();
                List<java.nio.file.attribute.AclEntry> acl = aclAttr.getAcl();
                acl.add(entry);
                aclAttr.setAcl(acl);
                logger.debug("Ustawiono uprawnienia dla katalogu: {}", path);
            } else {
                logger.warn("ACL nie jest obsługiwane na tej platformie dla ścieżki: {}", path);
            }
        } catch (Exception e) {
            logger.warn("Nie udało się ustawić uprawnień dla katalogu {}: {}", path, e.getMessage());
        }
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
    public boolean isFileAccessible(Path filePath) {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            logger.debug("Plik {} nie istnieje lub nie jest zwykłym plikiem", filePath);
            return true;
        }
        try {
            Files.newOutputStream(filePath, StandardOpenOption.WRITE, StandardOpenOption.APPEND).close();
            logger.debug("Plik {} jest dostępny do zapisu", filePath);
            return true;
        } catch (IOException e) {
            logger.warn("Plik {} jest niedostępny: {}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * Sanitizuje nazwę pliku lub katalogu, usuwając niedozwolone znaki i opcjonalnie skracając dla plików .MPF.
     *
     * @param name nazwa do sanitizacji
     * @param defaultName domyślna nazwa w razie null/pustej wartości
     * @param isMpf czy nazwa dotyczy pliku .MPF
     * @return sanitizowana nazwa
     */
    public String sanitizeName(String name, String defaultName, boolean isMpf) {
        if (name == null || name.trim().isEmpty()) {
            return defaultName;
        }

        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("[ąĄ]", "a")
                .replaceAll("[ćĆ]", "c")
                .replaceAll("[ęĘ]", "e")
                .replaceAll("[łŁ]", "l")
                .replaceAll("[ńŃ]", "n")
                .replaceAll("[óÓ]", "o")
                .replaceAll("[śŚ]", "s")
                .replaceAll("[źŹ]", "z")
                .replaceAll("[żŻ]", "z");

        String sanitized = normalized.replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_");

        if (isMpf) {
            String ext = ".MPF";
            String nameWithoutExt = sanitized.replaceAll("(\\.MPF)+$", "");
            String suffix = "";
            String baseName = nameWithoutExt;
            String macPattern = "_[Mm][Aa][Cc]\\d+";
            String subPattern = "_[A-Za-z]";
            String versionPattern = "_[Vv]\\d+";

            if (nameWithoutExt.matches(".*" + macPattern + subPattern + versionPattern + "$")) {
                int macIndex = nameWithoutExt.toLowerCase().lastIndexOf("_mac");
                suffix = nameWithoutExt.substring(macIndex);
                baseName = nameWithoutExt.substring(0, macIndex);
            } else if (nameWithoutExt.matches(".*" + macPattern + subPattern + "$")) {
                int macIndex = nameWithoutExt.toLowerCase().lastIndexOf("_mac");
                suffix = nameWithoutExt.substring(macIndex);
                baseName = nameWithoutExt.substring(0, macIndex);
            } else if (nameWithoutExt.matches(".*" + macPattern + "$")) {
                int macIndex = nameWithoutExt.toLowerCase().lastIndexOf("_mac");
                suffix = nameWithoutExt.substring(macIndex);
                baseName = nameWithoutExt.substring(0, macIndex);
            }

            int maxBaseLength = 24 - suffix.length() - ext.length();
            if (maxBaseLength < 0) {
                maxBaseLength = 0;
            }

            if (baseName.length() > maxBaseLength) {
                baseName = baseName.substring(0, maxBaseLength);
            }

            return baseName + suffix + ext;
        }

        return sanitized;
    }

    /**
     * Sanitizuje nazwę pliku lub katalogu, używając domyślnej wartości.
     *
     * @param name nazwa do sanitizacji
     * @param defaultName domyślna nazwa
     * @return sanitizowana nazwa
     */
    public String sanitizeName(String name, String defaultName) {
        return sanitizeName(name, defaultName, false);
    }

    /**
     * Waliduje załącznik, sprawdzając jego poprawność.
     *
     * @param file załącznik do walidacji
     * @throws IllegalArgumentException w przypadku niepoprawnego załącznika
     */
    private void validateAttachment(ProductionFileInfo file) {
        if (file.getFileName() == null || file.getFileName().isEmpty()) {
            throw new IllegalArgumentException("Nazwa pliku nie może być pusta");
        }
        if (file.getFileContent() == null || file.getFileContent().length == 0) {
            throw new IllegalArgumentException("Zawartość pliku nie może być pusta: " + file.getFileName());
        }
        if (file.getFileName().toLowerCase().endsWith(".mpf")) {
            // Przykład walidacji dla .MPF - można rozszerzyć o specyficzne wymagania
            if (file.getFileSize() > 10 * 1024 * 1024) { // Max 10MB
                throw new IllegalArgumentException("Plik .MPF jest za duży: " + file.getFileName());
            }
        }
    }
}