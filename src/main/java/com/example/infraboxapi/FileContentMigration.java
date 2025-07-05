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

        // Pobierz wszystkie rekordy ProductionFileInfo z ProductionQueueItem
        List<ProductionFileInfo> fileInfos = productionFileInfoRepository.findAllWithQueueItem();
        logger.info("Found {} records to migrate.", fileInfos.size());

        for (ProductionFileInfo fileInfo : fileInfos) {
            try {
                // Sprawdź, czy fileContent istnieje i filePath nie jest jeszcze ustawiony
                if (fileInfo.getFileContent() != null && fileInfo.getFilePath() == null) {
                    // Pobierz ProductionQueueItem
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

                    // Utwórz ścieżkę do pliku: Uploads/id_projektu/orderName/partName
                    String sanitizedOrderName = sanitizeName(orderName);
                    String sanitizedPartName = sanitizeName(partName);
                    String sanitizedFileName = sanitizeName(fileName);
                    Path dirPath = Paths.get(uploadDir, String.valueOf(projectId), sanitizedOrderName, sanitizedPartName);
                    Path filePath = dirPath.resolve(sanitizedFileName);

                    // Utwórz katalogi, jeśli nie istnieją
                    Files.createDirectories(dirPath);

                    // Zapisz fileContent na dysku
                    Files.write(filePath, fileInfo.getFileContent(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    logger.info("Saved file to: {}", filePath);

                    // Zaktualizuj filePath w rekordzie
                    fileInfo.setFilePath(filePath.toString());
                    fileInfo.setFileContent(null); // Usuń fileContent, aby zwolnić pamięć
                    productionFileInfoRepository.save(fileInfo);
                    logger.info("Updated file ID: {} with filePath: {}", fileInfo.getId(), filePath);
                } else {
                    logger.debug("File ID: {} already migrated or no fileContent.", fileInfo.getId());
                }
            } catch (IOException e) {
                logger.error("Failed to migrate file ID: {}. Error: {}", fileInfo.getId(), e.getMessage(), e);
                // Kontynuuj migrację dla pozostałych plików
            }
        }

        logger.info("Migration completed successfully.");
    }

    private String sanitizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Unknown_" + System.currentTimeMillis();
        }
        return name.replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_");
    }
}