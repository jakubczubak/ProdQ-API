package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.bind.DatatypeConverter;

@Service
public class FileSystemService {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemService.class);

    private final ProductionQueueItemRepository productionQueueItemRepository;
    private final SanitizerFactory sanitizerFactory;

    public FileSystemService(ProductionQueueItemRepository productionQueueItemRepository, SanitizerFactory sanitizerFactory) {
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.sanitizerFactory = sanitizerFactory;
    }

    public String sanitizeName(String name, String defaultName) {
        if (name == null || name.trim().isEmpty()) {
            return defaultName;
        }

        // Pobierz strategię z fabryki (na razie na sztywno dla 'inframet')
        FileNameSanitizerStrategy strategy = sanitizerFactory.getStrategy("inframet");

        // Zdefiniuj opcje (w przyszłości mogą pochodzić z bazy danych)
        Map<String, Object> options = Map.of("maxLength", 24);

        // Użyj strategii do oczyszczenia nazwy
        return strategy.sanitize(name, options);
    }

    public String sanitizeName(String name, String defaultName, boolean isMpf) {
        // Ta metoda jest teraz uproszczona i wywołuje główną metodę.
        // Parametr 'isMpf' nie jest już potrzebny tutaj, bo decyduje o tym strategia.
        return sanitizeName(name, defaultName);
    }

    public void synchronizeFiles(String programPath, String orderName, String partName, List<ProductionFileInfo> files) throws IOException {
        Path basePath = createDirectoryStructure(programPath, orderName, partName);

        long existingFilesCount = Files.exists(basePath)
                ? Files.list(basePath).filter(Files::isRegularFile).count()
                : 0;
        logger.debug("Before synchronization: {} files in directory {}", existingFilesCount, basePath);

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
                logger.error("Directory {} is not writable", basePath);
                throw new IOException("No write permissions for directory: " + basePath);
            }
            Files.createDirectories(tempDir);
            logger.trace("Created temporary directory: {}", tempDir);

            if (files != null && !files.isEmpty()) {
                for (ProductionFileInfo file : files) {
                    String fileName = file.getFileName();
                    validateAttachment(file);
                    Path tempFilePath = tempDir.resolve(fileName);
                    Path sourceFilePath = Paths.get(file.getFilePath());
                    Path destinationFilePath = basePath.resolve(fileName);

                    if (Files.exists(destinationFilePath) && isFileAccessible(destinationFilePath) && contentMatches(destinationFilePath, sourceFilePath)) {
                        logger.debug("File content in {} is unchanged, skipping write", destinationFilePath);
                        continue;
                    }

                    try {
                        logger.debug("Attempting to write temporary file: {}, size: {} bytes", tempFilePath, file.getFileSize());
                        Files.copy(sourceFilePath, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
                        logger.debug("Wrote temporary file: {}", tempFilePath);
                    } catch (IOException e) {
                        logger.error("Error writing temporary file {}: {}", tempFilePath, e.getMessage());
                        throw new IOException("Failed to write temporary file: " + tempFilePath, e);
                    }
                }
            }

            for (String diskFile : existingFiles) {
                if (!allAppFiles.contains(diskFile)) {
                    Path filePath = basePath.resolve(diskFile);
                    if (isFileAccessible(filePath)) {
                        Files.deleteIfExists(filePath);
                        logger.debug("Deleted unused file: {}", filePath);
                    } else {
                        logger.warn("Cannot delete file {}, it is locked", filePath);
                    }
                }
            }

            if (files != null && !files.isEmpty()) {
                for (ProductionFileInfo file : files) {
                    String fileName = file.getFileName();
                    Path tempFilePath = tempDir.resolve(fileName);
                    Path filePath = basePath.resolve(fileName);

                    if (!Files.exists(tempFilePath)) {
                        continue;
                    }

                    if (Files.exists(filePath)) {
                        if (!isFileAccessible(filePath)) {
                            logger.warn("File {} is locked, saving under unique name", filePath);
                            filePath = getUniqueFilePath(basePath, fileName);
                        } else {
                            logger.debug("File {} exists, will be overwritten", filePath);
                        }
                    }

                    try {
                        logger.debug("Attempting to move file from {} to {}", tempFilePath, filePath);
                        Files.move(tempFilePath, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                        logger.debug("Wrote file: {}, size: {} bytes", filePath, file.getFileSize());
                    } catch (IOException e) {
                        logger.error("Error moving file from {} to {}: {}", tempFilePath, filePath, e.getMessage());
                        throw new IOException("Failed to move file: " + tempFilePath + " -> " + filePath, e);
                    }
                }
            }

            long finalFilesCount = Files.exists(basePath)
                    ? Files.list(basePath).filter(Files::isRegularFile).count()
                    : 0;
            logger.debug("After synchronization: {} files in directory {}", finalFilesCount, basePath);

        } finally {
            if (Files.exists(tempDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
                    for (Path file : stream) {
                        Files.deleteIfExists(file);
                    }
                    Files.deleteIfExists(tempDir);
                    logger.trace("Deleted temporary directory: {}", tempDir);
                } catch (IOException e) {
                    logger.warn("Failed to delete temporary directory {}: {}", tempDir, e.getMessage());
                }
            }
        }
    }

    private boolean contentMatches(Path filePath1, Path filePath2) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] fileContent1 = Files.readAllBytes(filePath1);
            byte[] fileContent2 = Files.readAllBytes(filePath2);
            String fileHash1 = DatatypeConverter.printHexBinary(md.digest(fileContent1));
            String fileHash2 = DatatypeConverter.printHexBinary(md.digest(fileContent2));
            return fileHash1.equals(fileHash2);
        } catch (Exception e) {
            logger.warn("Error computing hash for files {} and {}: {}", filePath1, filePath2, e.getMessage());
            return false;
        }
    }

    private Path createDirectoryStructure(String programPath, String orderName, String partName) throws IOException {
        Path basePath = Paths.get(programPath, orderName, partName);
        try {
            Files.createDirectories(basePath);
            logger.debug("Created or used existing directory structure: {}", basePath);
            return basePath;
        } catch (FileAlreadyExistsException e) {
            if (!Files.isDirectory(basePath)) {
                logger.error("Path {} exists as a file, cannot create directory", basePath);
                throw new IOException("Path exists as a file: " + basePath, e);
            }
            logger.debug("Directory {} already exists", basePath);
            return basePath;
        } catch (IOException e) {
            logger.error("Error creating directory {}: {}", basePath, e.getMessage());
            throw new IOException("Failed to create directory: " + basePath, e);
        }
    }

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
                throw new IOException("Cannot find unique name for file: " + fileName);
            }
        }
    }

    public boolean isFileAccessible(Path filePath) {
        if (!Files.exists(filePath)) {
            logger.debug("File {} does not exist", filePath);
            return true;
        }
        if (!Files.isRegularFile(filePath)) {
            logger.debug("Path {} is not a regular file", filePath);
            return true;
        }
        try {
            Files.newOutputStream(filePath, StandardOpenOption.WRITE, StandardOpenOption.APPEND).close();
            logger.debug("File {} is accessible for writing", filePath);
            return true;
        } catch (IOException e) {
            logger.warn("File {} is inaccessible (likely locked): {}", filePath, e.getMessage(), e);
            return false;
        }
    }

    public boolean isDirectoryAccessible(Path dirPath) {
        if (!Files.exists(dirPath)) {
            logger.debug("Directory {} does not exist", dirPath);
            return true;
        }
        if (!Files.isDirectory(dirPath)) {
            logger.debug("Path {} is not a directory", dirPath);
            return true;
        }
        try {
            Path tempFile = dirPath.resolve("temp_" + System.currentTimeMillis() + ".tmp");
            Files.createFile(tempFile);
            Files.deleteIfExists(tempFile);
            logger.debug("Directory {} is accessible for writing and deletion", dirPath);
            return true;
        } catch (IOException e) {
            logger.warn("Directory {} is inaccessible (likely locked): {}", dirPath, e.getMessage(), e);
            return false;
        }
    }

    private void validateAttachment(ProductionFileInfo file) {
        if (file.getFileName() == null || file.getFileName().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be empty");
        }
        if (file.getFilePath() == null || file.getFilePath().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be empty: " + file.getFileName());
        }
        if (file.getFileName().toLowerCase().endsWith(".mpf")) {
            if (file.getFileSize() > 10 * 1024 * 1024) { // Max 10MB
                throw new IllegalArgumentException("MPF file is too large: " + file.getFileName());
            }
        }
    }
}