package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ToolListGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(ToolListGeneratorService.class);

    private static final String TOOL_LIST_FILENAME = "TOOLS.txt";

    private static final Pattern TOOL_PATTERN = Pattern.compile(
            "^;\\s*-\\s*(.*?)\\s*-\\s*Poziom Z min:\\s*(-?\\d+\\.?\\d*)"
    );

    @Value("${file.upload-dir:Uploads}")
    private String uploadDir;

    /**
     * Generuje plik TOOLS.txt. Jeśli ProductionFileInfo dla tego pliku już istnieje,
     * aktualizuje go. W przeciwnym razie tworzy nowy.
     * @param item Program produkcyjny, dla którego generowana jest lista.
     * @return Optional z utworzonym lub zaktualizowanym ProductionFileInfo.
     */
    @Transactional
    public Optional<ProductionFileInfo> generateAndStoreToolList(ProductionQueueItem item) {
        logger.info("Starting tool list generation for item ID: {}", item.getId());

        Map<String, Double> allTools = new HashMap<>();

        List<ProductionFileInfo> mpfFiles = item.getFiles().stream()
                .filter(f -> f.getFileName().toLowerCase().endsWith(".mpf"))
                .collect(Collectors.toList());

        for (ProductionFileInfo mpfFile : mpfFiles) {
            try {
                Map<String, Double> toolsFromFile = extractToolsFromMpf(Paths.get(mpfFile.getFilePath()));
                toolsFromFile.forEach((toolName, zMin) ->
                        allTools.merge(toolName, zMin, Math::max)
                );
            } catch (IOException e) {
                logger.error("Could not read MPF file {} to extract tools. Error: {}", mpfFile.getFilePath(), e.getMessage());
            }
        }

        if (allTools.isEmpty()) {
            logger.warn("No tools found for item ID {}. No tool list will be generated.", item.getId());
            return Optional.empty();
        }

        try {
            Path destinationPath;
            if (!mpfFiles.isEmpty()) {
                Path firstMpfPath = Paths.get(mpfFiles.get(0).getFilePath());
                destinationPath = firstMpfPath.getParent();
            } else {
                destinationPath = getLocalUploadPath(item);
                logger.warn("No MPF files found. Falling back to default path logic: {}", destinationPath);
            }

            // KLUCZOWA ZMIANA: Znajdź istniejący plik lub przygotuj się do utworzenia nowego.
            Optional<ProductionFileInfo> existingToolFile = item.getFiles().stream()
                    .filter(f -> TOOL_LIST_FILENAME.equalsIgnoreCase(f.getFileName()))
                    .findFirst();

            if (existingToolFile.isPresent()) {
                logger.info("Found existing ProductionFileInfo for {}. It will be updated.", TOOL_LIST_FILENAME);
            }

            ProductionFileInfo managedFile = writeToolListAndManageEntity(allTools, item, destinationPath, existingToolFile);
            return Optional.of(managedFile);

        } catch (IOException e) {
            logger.error("Failed to write tool list file for item ID {}. Error: {}", item.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Double> extractToolsFromMpf(Path mpfFilePath) throws IOException {
        Map<String, Double> tools = new HashMap<>();
        if (!Files.exists(mpfFilePath)) {
            logger.warn("MPF file does not exist: {}", mpfFilePath);
            return tools;
        }

        boolean inToolListSection = false;
        List<String> lines = Files.readAllLines(mpfFilePath, StandardCharsets.UTF_8);

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (inToolListSection) {
                Matcher matcher = TOOL_PATTERN.matcher(trimmedLine);
                if (matcher.find()) {
                    String toolName = matcher.group(1).trim();
                    try {
                        double zMin = Double.parseDouble(matcher.group(2));
                        tools.put(toolName, zMin);
                    } catch (NumberFormatException e) {
                        logger.warn("Could not parse Z min value for tool '{}' in file {}", toolName, mpfFilePath);
                    }
                } else {
                    break;
                }
            } else {
                if (trimmedLine.toUpperCase().contains("LISTA NARZEDZI:")) {
                    inToolListSection = true;
                }
            }
        }
        return tools;
    }

    /**
     * Zapisuje listę narzędzi do pliku, a następnie tworzy nową encję ProductionFileInfo
     * lub aktualizuje istniejącą, jeśli została dostarczona.
     */
    private ProductionFileInfo writeToolListAndManageEntity(Map<String, Double> tools, ProductionQueueItem item, Path destinationPath, Optional<ProductionFileInfo> existingFileOpt) throws IOException {
        Path toolListPath = destinationPath.resolve(TOOL_LIST_FILENAME);

        List<Map.Entry<String, Double>> sortedTools = tools.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());

        StringBuilder content = new StringBuilder();
        for (int i = 0; i < sortedTools.size(); i++) {
            Map.Entry<String, Double> entry = sortedTools.get(i);
            content.append(String.format("%d. %s (Z min: %.2f)\n", i + 1, entry.getKey(), entry.getValue()));
        }

        Files.createDirectories(destinationPath);
        Files.writeString(toolListPath, content.toString(), StandardCharsets.UTF_8);
        long fileSize = Files.size(toolListPath);

        if (existingFileOpt.isPresent()) {
            // AKTUALIZUJ ISTNIEJĄCĄ ENCJE
            ProductionFileInfo fileToUpdate = existingFileOpt.get();
            fileToUpdate.setFileSize(fileSize);
            fileToUpdate.setFilePath(toolListPath.toString());
            fileToUpdate.setCompleted(true);
            logger.info("Successfully updated existing ProductionFileInfo (ID: {}) for {}", fileToUpdate.getId(), TOOL_LIST_FILENAME);
            return fileToUpdate;
        } else {
            // UTWÓRZ NOWĄ ENCJE
            logger.info("Creating new ProductionFileInfo for {}", TOOL_LIST_FILENAME);
            return ProductionFileInfo.builder()
                    .fileName(TOOL_LIST_FILENAME)
                    .fileType("text/plain")
                    .fileSize(fileSize)
                    .filePath(toolListPath.toString())
                    .productionQueueItem(item)
                    .completed(true)
                    .build();
        }
    }

    private Path getLocalUploadPath(ProductionQueueItem item) {
        String sanitizedOrderName = item.getOrderName().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String sanitizedPartName = item.getPartName().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return Paths.get(uploadDir, String.valueOf(item.getId()), sanitizedOrderName, sanitizedPartName);
    }
}