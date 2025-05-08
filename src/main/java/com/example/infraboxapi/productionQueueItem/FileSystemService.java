package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.bind.DatatypeConverter;

/**
 * Service responsible for file system operations, including directory creation, file writing, and deletion.
 */
@Service
public class FileSystemService {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemService.class);

    private final ProductionQueueItemRepository productionQueueItemRepository;

    public FileSystemService(ProductionQueueItemRepository productionQueueItemRepository) {
        this.productionQueueItemRepository = productionQueueItemRepository;
    }

    /**
     * Synchronizes attachments with the machine's directory, overwriting existing files and removing unused ones.
     * If a file is locked, it is saved under a unique name with a suffix.
     * Uses the same directory for identical orderName and partName.
     *
     * @param programPath Path to the machine's program directory
     * @param orderName   Order name
     * @param partName    Part name
     * @param files       List of attachments to synchronize
     * @throws IOException If a file operation fails
     */
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

                    byte[] content = file.getFileContent();
                    if (content == null || content.length == 0) {
                        logger.error("File content for {} is empty or null", fileName);
                        throw new IllegalArgumentException("Empty file content: " + fileName);
                    }

                    // Check if file content has changed
                    Path filePath = basePath.resolve(fileName);
                    if (Files.exists(filePath) && isFileAccessible(filePath) && contentMatches(filePath, content)) {
                        logger.debug("File {} content unchanged, skipping write", filePath);
                        continue;
                    }

                    try {
                        logger.debug("Attempting to write temporary file: {}, size: {} bytes", tempFilePath, content.length);
                        Files.write(tempFilePath, content);
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
                        logger.debug("Deleted unused file: {}, time: {}", filePath, Instant.now());
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
                        continue; // File was not written (content unchanged)
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
                        logger.debug("Wrote file: {}, size: {} bytes, time: {}", filePath, file.getFileSize(), Instant.now());
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

    /**
     * Checks if the file content matches the provided byte array using MD5 hash.
     *
     * @param filePath Path to the existing file
     * @param content  Byte array to compare
     * @return true if content matches, false otherwise
     */
    private boolean contentMatches(Path filePath, byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] fileContent = Files.readAllBytes(filePath);
            String fileHash = DatatypeConverter.printHexBinary(md.digest(fileContent));
            String contentHash = DatatypeConverter.printHexBinary(md.digest(content));
            return fileHash.equals(contentHash);
        } catch (Exception e) {
            logger.warn("Error computing hash for file {}: {}", filePath, e.getMessage());
            return false; // Assume different in case of error
        }
    }

    /**
     * Creates the directory structure for the given order and part names, always using the original partName.
     *
     * @param programPath Path to the machine's program directory
     * @param orderName   Order name
     * @param partName    Part name
     * @return Path to the part directory
     * @throws IOException If a file operation fails
     */
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

    /**
     * Generates a unique file path by adding a version number (_v2, _v3, etc.) in case of name conflicts.
     *
     * @param basePath Base directory
     * @param fileName File name
     * @return Unique file path
     * @throws IOException If a unique name cannot be found
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
                throw new IOException("Cannot find unique name for file: " + fileName);
            }
        }
    }

    /**
     * Checks if a file is accessible (not locked) for deletion or overwriting.
     *
     * @param filePath Path to the file
     * @return true if the file is accessible, false otherwise
     */
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

    /**
     * Checks if a directory is accessible (not locked) for deletion.
     *
     * @param dirPath Path to the directory
     * @return true if the directory is accessible, false otherwise
     */
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
            // Try creating a temporary file in the directory to check accessibility
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

    /**
     * Sanitizes a file or directory name, removing invalid characters and optionally truncating for .MPF files.
     *
     * @param name        Name to sanitize
     * @param defaultName Default name if null or empty
     * @param isMpf       Whether the name is for an .MPF file
     * @return Sanitized name
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
     * Sanitizes a file or directory name, using a default value.
     *
     * @param name        Name to sanitize
     * @param defaultName Default name
     * @return Sanitized name
     */
    public String sanitizeName(String name, String defaultName) {
        return sanitizeName(name, defaultName, false);
    }

    /**
     * Validates an attachment, checking its correctness.
     *
     * @param file Attachment to validate
     * @throws IllegalArgumentException If the attachment is invalid
     */
    private void validateAttachment(ProductionFileInfo file) {
        if (file.getFileName() == null || file.getFileName().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be empty");
        }
        if (file.getFileContent() == null || file.getFileContent().length == 0) {
            throw new IllegalArgumentException("File content cannot be empty: " + file.getFileName());
        }
        if (file.getFileName().toLowerCase().endsWith(".mpf")) {
            // Example validation for .MPF - can be extended for specific requirements
            if (file.getFileSize() > 10 * 1024 * 1024) { // Max 10MB
                throw new IllegalArgumentException("MPF file is too large: " + file.getFileName());
            }
        }
    }
}