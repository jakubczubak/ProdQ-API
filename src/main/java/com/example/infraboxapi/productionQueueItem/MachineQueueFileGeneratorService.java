package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.materialReservation.MaterialReservation;
import com.example.infraboxapi.materialReservation.MaterialProfile;
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
        return productionQueueItemRepository.findByQueueTypeWithFilesAndMaterial(queueType)
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

            content.append("---\n\n");
            appendHeader(content, program);
            content.append("\n--- PROGRAMY ---\n\n");

            if (mpfFiles.isEmpty()) {
                // No MPF files - display informational message
                content.append("# Brak przypisanych programów do zlecenia produkcyjnego.\n");
            } else {
                // Has MPF files - list them
                position = appendProgramFiles(content, mpfFiles, position);
            }
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

        // Dodaj informacje o materiale jeśli istnieje
        String materialInfo = buildMaterialInfo(program);
        if (materialInfo != null && !materialInfo.isEmpty()) {
            content.append("------------------------------------------------------------\n");
            content.append(materialInfo);
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

    private String buildMaterialInfo(ProductionQueueItem program) {
        if (program.getMaterialReservation() == null) {
            return " Materiał         : Nie zdefiniowano\n";
        }

        MaterialReservation reservation = program.getMaterialReservation();
        String materialDesc;

        if (reservation.getIsCustom()) {
            materialDesc = buildCustomMaterialDescription(reservation);
        } else if (reservation.getMaterial() != null) {
            Material material = reservation.getMaterial();
            materialDesc = buildStandardMaterialDescription(material, reservation);
        } else {
            return " Materiał         : Nie zdefiniowano\n";
        }

        return String.format(" Materiał         : %s\n", materialDesc);
    }

    private String buildStandardMaterialDescription(Material material, MaterialReservation reservation) {
        StringBuilder desc = new StringBuilder();

        // Nazwa materiału (bez wymiarów)
        String materialName = material.getName();
        // Usuwamy wymiary z nazwy (np. "Płyty PA13: 19x415x575" -> "Płyty PA13")
        materialName = materialName.replaceAll(":\\s*[0-9x⌀Ø×]+\\s*$", "").trim();
        desc.append(materialName);

        // Typ materiału w nawiasach (MaterialGroup.MaterialType.name)
        if (material.getMaterialGroup() != null &&
            material.getMaterialGroup().getMaterialType() != null &&
            material.getMaterialGroup().getMaterialType().getName() != null) {
            desc.append(String.format(" (%s)", material.getMaterialGroup().getMaterialType().getName()));
        }

        // Wymiary materiału
        String dimensions = formatMaterialDimensions(material);
        if (!dimensions.isEmpty()) {
            desc.append(String.format(" | %s", dimensions));
        }

        // Ilość/Długość potrzebna
        if (reservation.getQuantityOrLength() != null) {
            String unit = determineMaterialUnit(material);
            desc.append(String.format(" | Potrzebne: %.2f %s", reservation.getQuantityOrLength(), unit));
        }

        return desc.toString();
    }

    private String buildCustomMaterialDescription(MaterialReservation reservation) {
        StringBuilder desc = new StringBuilder();

        if (reservation.getCustomName() != null) {
            desc.append(reservation.getCustomName());
        } else {
            desc.append("Materiał niestandardowy");
        }

        if (reservation.getCustomMaterialType() != null && reservation.getCustomMaterialType().getName() != null) {
            desc.append(String.format(" (%s)", reservation.getCustomMaterialType().getName()));
        }

        String dimensions = formatCustomMaterialDimensions(reservation);
        if (!dimensions.isEmpty()) {
            desc.append(String.format(" | %s", dimensions));
        }

        if (reservation.getQuantityOrLength() != null) {
            String unit = determineCustomMaterialUnit(reservation);
            desc.append(String.format(" | Potrzebne: %.2f %s", reservation.getQuantityOrLength(), unit));
        }

        return desc.toString();
    }

    private String formatMaterialDimensions(Material material) {
        // Pobierz typ z materialGroup
        String materialGroupType = null;
        if (material.getMaterialGroup() != null && material.getMaterialGroup().getType() != null) {
            materialGroupType = material.getMaterialGroup().getType().toLowerCase();
        }

        // Rura (tube): ma diameter i thickness, wyświetl średnicę zewnętrzną i wewnętrzną
        if ("tube".equals(materialGroupType) && material.getDiameter() > 0 && material.getThickness() > 0) {
            // Średnica wewnętrzna = diameter - 2 * thickness
            float innerDiameter = material.getDiameter() - (2 * material.getThickness());
            return String.format("Ø%.0fxØ%.0fmm", material.getDiameter(), innerDiameter);
        }

        // Pręt (rod): ma tylko diameter, wyświetl tylko średnicę
        if ("rod".equals(materialGroupType) && material.getDiameter() > 0) {
            return String.format("Ø%.0fmm", material.getDiameter());
        }

        // Płyta (plate): ma x, y, z
        if ("plate".equals(materialGroupType) && (material.getX() > 0 || material.getY() > 0 || material.getZ() > 0)) {
            List<String> dims = new java.util.ArrayList<>();
            if (material.getX() > 0) dims.add(String.format("%.0f", material.getX()));
            if (material.getY() > 0) dims.add(String.format("%.0f", material.getY()));
            if (material.getZ() > 0) dims.add(String.format("%.0f", material.getZ()));
            return String.join("x", dims) + "mm";
        }

        // Fallback: jeśli nie ma typu w materialGroup, użyj starej logiki
        if (material.getDiameter() > 0 && material.getThickness() > 0) {
            float innerDiameter = material.getDiameter() - (2 * material.getThickness());
            return String.format("Ø%.0fxØ%.0fmm", material.getDiameter(), innerDiameter);
        }
        if (material.getDiameter() > 0) {
            return String.format("Ø%.0fmm", material.getDiameter());
        }
        if (material.getX() > 0 || material.getY() > 0 || material.getZ() > 0) {
            List<String> dims = new java.util.ArrayList<>();
            if (material.getX() > 0) dims.add(String.format("%.0f", material.getX()));
            if (material.getY() > 0) dims.add(String.format("%.0f", material.getY()));
            if (material.getZ() > 0) dims.add(String.format("%.0f", material.getZ()));
            return String.join("x", dims) + "mm";
        }

        return "";
    }

    private String formatCustomMaterialDimensions(MaterialReservation reservation) {
        MaterialProfile profile = reservation.getCustomType();

        if (profile == MaterialProfile.PLATE) {
            // Płyta: 415x575x19mm
            List<String> dims = new java.util.ArrayList<>();
            if (reservation.getCustomX() != null && reservation.getCustomX() > 0)
                dims.add(String.format("%.0f", reservation.getCustomX()));
            if (reservation.getCustomY() != null && reservation.getCustomY() > 0)
                dims.add(String.format("%.0f", reservation.getCustomY()));
            if (reservation.getCustomZ() != null && reservation.getCustomZ() > 0)
                dims.add(String.format("%.0f", reservation.getCustomZ()));
            return dims.isEmpty() ? "" : String.join("x", dims) + "mm";

        } else if (profile == MaterialProfile.TUBE) {
            // Rura: Ø100xØ80mm
            if (reservation.getCustomDiameter() != null && reservation.getCustomDiameter() > 0 &&
                reservation.getCustomInnerDiameter() != null && reservation.getCustomInnerDiameter() > 0) {
                return String.format("Ø%.0fxØ%.0fmm", reservation.getCustomDiameter(), reservation.getCustomInnerDiameter());
            } else if (reservation.getCustomDiameter() != null && reservation.getCustomDiameter() > 0) {
                return String.format("Ø%.0fmm", reservation.getCustomDiameter());
            }

        } else if (profile == MaterialProfile.ROD) {
            // Pręt: Ø100mm
            if (reservation.getCustomDiameter() != null && reservation.getCustomDiameter() > 0) {
                return String.format("Ø%.0fmm", reservation.getCustomDiameter());
            }
        }

        return "";
    }

    private String determineMaterialUnit(Material material) {
        // Rury i pręty (mają diameter) - jednostka to długość w mm
        if (material.getDiameter() > 0) {
            return "mm";
        }
        // Płyty (mają x, y, z) - jednostka to sztuki
        return "szt";
    }

    private String determineCustomMaterialUnit(MaterialReservation reservation) {
        MaterialProfile profile = reservation.getCustomType();
        if (profile == MaterialProfile.ROD || profile == MaterialProfile.TUBE) {
            return "mm";
        }
        return "szt";
    }




}