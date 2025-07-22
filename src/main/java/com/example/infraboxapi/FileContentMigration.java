package com.example.infraboxapi;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfoRepository;
import com.example.infraboxapi.productionQueueItem.ProductionQueueItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Component
public class FileContentMigration {

    private static final Logger logger = LoggerFactory.getLogger(FileContentMigration.class);

    private final ProductionFileInfoRepository productionFileInfoRepository;

    @Value("${file.upload-dir:Uploads}")
    private String uploadDir;

    @Autowired
    public FileContentMigration(ProductionFileInfoRepository productionFileInfoRepository) {
        this.productionFileInfoRepository = productionFileInfoRepository;
    }

    @Transactional
    public void migrateFileContentToDisk() {
        logger.info("Starting migration of fileContent to disk...");

        List<ProductionFileInfo> fileInfos = productionFileInfoRepository.findAllWithQueueItem();
        logger.info("Found {} records to migrate to disk.", fileInfos.size());

        for (ProductionFileInfo fileInfo : fileInfos) {
            try {
                if (fileInfo.getFileContent() != null && fileInfo.getFilePath() == null) {
                    ProductionQueueItem queueItem = fileInfo.getProductionQueueItem();
                    if (queueItem == null) {
                        logger.warn("No ProductionQueueItem for file ID: {}. Skipping.", fileInfo.getId());
                        continue;
                    }

                    Integer projectId = queueItem.getId();
                    String orderName = queueItem.getOrderName();
                    String partName = queueItem.getPartName();
                    String fileName = fileInfo.getFileName();

                    if (projectId == null || orderName == null || partName == null || fileName == null) {
                        logger.warn("Invalid data for file ID: {}. ProjectId: {}, OrderName: {}, PartName: {}, FileName: {}. Skipping.",
                                fileInfo.getId(), projectId, orderName, partName, fileName);
                        continue;
                    }

                    String sanitizedOrderName = sanitizeName(orderName);
                    String sanitizedPartName = sanitizeName(partName);
                    String sanitizedFileName = sanitizeName(fileName);
                    Path dirPath = Paths.get(uploadDir, String.valueOf(projectId), sanitizedOrderName, sanitizedPartName);
                    Path filePath = dirPath.resolve(sanitizedFileName);

                    Files.createDirectories(dirPath);
                    Files.write(filePath, fileInfo.getFileContent(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    logger.info("Saved file to: {}", filePath);

                    fileInfo.setFilePath(filePath.toString());
                    fileInfo.setFileContent(null);
                    productionFileInfoRepository.save(fileInfo);
                    logger.info("Updated file ID: {} with filePath: {}", fileInfo.getId(), filePath);
                } else {
                    logger.debug("File ID: {} already migrated to disk or no fileContent.", fileInfo.getId());
                }
            } catch (IOException e) {
                logger.error("Failed to migrate file ID: {} to disk. Error: {}", fileInfo.getId(), e.getMessage(), e);
            }
        }

        logger.info("Migration to disk completed.");
    }

    /**
     * Migrates file content from disk to the database for records where fileContent is null.
     */
    @Transactional
    public void migrateFileContentFromDisk() {
        logger.info("Starting migration of file content from disk to database...");

        List<ProductionFileInfo> fileInfos = productionFileInfoRepository.findAll();
        logger.info("Found {} total file records to check for migration from disk.", fileInfos.size());

        for (ProductionFileInfo fileInfo : fileInfos) {
            // Sprawdź, czy zawartość jest pusta, a ścieżka do pliku istnieje
            if (fileInfo.getFileContent() == null && fileInfo.getFilePath() != null) {
                try {
                    Path path = Paths.get(fileInfo.getFilePath());
                    if (Files.exists(path) && Files.isReadable(path)) {
                        // Odczytaj zawartość pliku z dysku
                        byte[] content = Files.readAllBytes(path);
                        fileInfo.setFileContent(content);
                        productionFileInfoRepository.save(fileInfo);
                        logger.info("Successfully migrated file content from disk for file ID: {}", fileInfo.getId());
                    } else {
                        logger.warn("File path for file ID: {} does not exist or is not readable: {}", fileInfo.getId(), fileInfo.getFilePath());
                    }
                } catch (IOException e) {
                    logger.error("Failed to read file from disk for file ID: {}. Error: {}", fileInfo.getId(), e.getMessage(), e);
                }
            } else {
                logger.debug("Skipping file ID: {}. Already has content or no file path.", fileInfo.getId());
            }
        }

        logger.info("Migration from disk to database completed.");
    }

    private String sanitizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Unknown_" + System.currentTimeMillis();
        }
        return name.replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_");
    }
}