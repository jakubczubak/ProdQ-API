package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MachineQueueFileGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(MachineQueueFileGeneratorService.class);

    private final MachineRepository machineRepository;
    private final ProductionQueueItemRepository productionQueueItemRepository;
    private final FileSystemService fileSystemService;

    public MachineQueueFileGeneratorService(
            MachineRepository machineRepository,
            ProductionQueueItemRepository productionQueueItemRepository,
            FileSystemService fileSystemService) {
        this.machineRepository = machineRepository;
        this.productionQueueItemRepository = productionQueueItemRepository;
        this.fileSystemService = fileSystemService;
    }

    public String generateQueueFileForMachine(String queueType) throws IOException {
        if (queueType == null || "ncQueue".equals(queueType) || "completed".equals(queueType)) {
            return "";
        }

        try {
            Integer machineId = Integer.parseInt(queueType);
            Optional<Machine> machineOpt = machineRepository.findById(machineId);
            if (machineOpt.isEmpty()) {
                return "";
            }

            Machine machine = machineOpt.get();
            Path filePath = resolvePath(machine);

            Files.createDirectories(filePath.getParent());

            List<ProductionQueueItem> programs = getSortedPrograms(queueType);
            String content = buildFileContent(programs);

            Files.writeString(filePath, content);
            return content;

        } catch (NumberFormatException e) {
            logger.warn("Invalid queueType: {}", queueType, e);
            return "";
        } catch (IOException e) {
            logger.error("Error generating queue file for queueType {}: {}", queueType, e.getMessage(), e);
            throw e;
        }
    }

    private Path resolvePath(Machine machine) {
        String fileName = fileSystemService.sanitizeName(machine.getMachineName(), "machine_queue") + ".txt";
        String cleanedPath = machine.getQueueFilePath().replaceFirst("^/+", "").replaceFirst("^cnc/?", "");
        String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local";
        Path mountDir = "prod".equalsIgnoreCase(appEnv) || "docker-local".equalsIgnoreCase(appEnv)
                ? Paths.get("/cnc")
                : Paths.get("./cnc");

        Path basePath = cleanedPath.isEmpty() ? mountDir : mountDir.resolve(cleanedPath).normalize();
        return basePath.resolve(fileName);
    }

    private List<ProductionQueueItem> getSortedPrograms(String queueType) {
        return productionQueueItemRepository.findByQueueType(queueType, Pageable.unpaged()).getContent()
                .stream()
                .sorted(Comparator.comparing(ProductionQueueItem::getOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private String buildFileContent(List<ProductionQueueItem> programs) {
        StringBuilder content = new StringBuilder();

        content.append("# Edytuj tylko statusy w nawiasach: [OK] lub [NOK].\n");
        content.append(String.format("# Wygenerowano: %s\n\n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

        if (programs.isEmpty()) {
            content.append("# Brak programów w kolejce dla tej maszyny.\n");
            return content.toString();
        }

        int position = 1;
        for (ProductionQueueItem program : programs) {
            List<ProductionFileInfo> mpfFiles = filterAndSortMpfFiles(program);
            if (mpfFiles.isEmpty()) {
                continue;
            }

            content.append("---\n\n");
            appendHeader(content, program);
            content.append("\n--- PROGRAMY ---\n\n");
            position = appendProgramFiles(content, mpfFiles, position);
        }
        content.append("\n---");
        return content.toString();
    }

    private List<ProductionFileInfo> filterAndSortMpfFiles(ProductionQueueItem program) {
        if (program.getFiles() == null) {
            return List.of();
        }
        return program.getFiles().stream()
                .filter(file -> file.getFileName().toLowerCase().endsWith(".mpf"))
                .sorted(Comparator.comparing(ProductionFileInfo::getOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private void appendHeader(StringBuilder content, ProductionQueueItem program) {
        content.append("/**\n");
        content.append("============================================================\n");

        appendHeaderLine(content, "ID Programu", String.valueOf(program.getId()));
        appendHeaderLine(content, "Zamówienie", program.getOrderName());
        appendHeaderLine(content, "Nazwa elementu", program.getPartName());
        appendHeaderLine(content, "Ilość", program.getQuantity() + " szt");
        String sanitizedOrderName = fileSystemService.sanitizeName(program.getOrderName(), "NoOrderName_" + program.getId());
        String sanitizedPartName = fileSystemService.sanitizeName(program.getPartName(), "NoPartName_" + program.getId());
        appendHeaderLine(content, "Lokalizacja", String.format("%s/%s/", sanitizedOrderName, sanitizedPartName));

        content.append("------------------------------------------------------------\n");

        appendHeaderLine(content, "Termin", program.getDeadline());
        appendHeaderLine(content, "Czas", program.getCamTime());
        appendHeaderLine(content, "Autor", program.getAuthor());

        String prepInfo = buildPreparationInfoString(program);
        if (prepInfo != null && !prepInfo.isEmpty()){
            content.append("------------------------------------------------------------\n");
            String[] prepParts = prepInfo.split("\\|");
            if (prepParts.length == 3) {
                appendHeaderLine(content, "Przygotówka", prepParts[0].trim() + " | " + prepParts[1].trim());
                appendHeaderLine(content, "Wymiary", prepParts[2].trim());
            }
        }

        String additionalInfo = program.getAdditionalInfo();
        if (additionalInfo != null && !additionalInfo.trim().isEmpty()) {
            content.append("------------------------------------------------------------\n");
            content.append(String.format(" Uwagi: %s\n", additionalInfo));
        }

        content.append("============================================================\n");
        content.append(" */");
    }

    private void appendHeaderLine(StringBuilder content, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        String paddedKey = String.format("%-17s", key);
        content.append(String.format(" %s: %s\n", paddedKey, value));
    }

    private int appendProgramFiles(StringBuilder content, List<ProductionFileInfo> mpfFiles, int startPosition) {
        int position = startPosition;
        final int fileNameWidth = 24;

        for (ProductionFileInfo mpfFile : mpfFiles) {
            String sanitizedMpfName = fileSystemService.sanitizeName(mpfFile.getFileName(), "NoFileName_" + mpfFile.getId());
            String status = mpfFile.isCompleted() ? "[OK]" : "[NOK]";

            String paddedFileName = String.format("%-" + fileNameWidth + "s", sanitizedMpfName);

            content.append(String.format("%d. %s | %s\n", position, paddedFileName, status));
            position++;
        }
        return position;
    }

    private String buildPreparationInfoString(ProductionQueueItem program) {
        if (program.getMaterialType() == null || program.getMaterialType().getName() == null || program.getMaterialProfile() == null) {
            return "";
        }
        String materialTypeName = program.getMaterialType().getName();
        String materialProfile = translateMaterialProfile(program.getMaterialProfile());

        StringBuilder dimensions = new StringBuilder();

        if ("Płyta".equals(materialProfile)) {
            if (program.getX() != null && program.getY() != null && program.getZ() != null) {
                dimensions.append(String.format(Locale.US, "%.2f x %.2f x %.2f mm",
                        program.getX().doubleValue(), program.getY().doubleValue(), program.getZ().doubleValue()));
            }
        } else if ("Rura".equals(materialProfile)) {
            if (program.getDiameter() != null && program.getInnerDiameter() != null && program.getLength() != null) {
                dimensions.append(String.format(Locale.US, "∅%.2f x ∅%.2f x %.2f mm",
                        program.getDiameter().doubleValue(), program.getInnerDiameter().doubleValue(), program.getLength().doubleValue()));
            }
        } else if ("Pręt".equals(materialProfile)) {
            if (program.getDiameter() != null && program.getLength() != null) {
                dimensions.append(String.format(Locale.US, "∅%.2f x %.2f mm",
                        program.getDiameter().doubleValue(), program.getLength().doubleValue()));
            }
        }

        if (dimensions.length() == 0) {
            return "";
        }
        return String.format("%s | %s | %s", materialTypeName, materialProfile, dimensions.toString());
    }

    private String translateMaterialProfile(String materialProfile) {
        if (materialProfile == null) return "";
        switch (materialProfile) {
            case "Plate": return "Płyta";
            case "Tube": return "Rura";
            case "Rod": return "Pręt";
            default: return materialProfile;
        }
    }
}