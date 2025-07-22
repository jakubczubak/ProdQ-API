package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable; // Upewnij się, że ten import istnieje
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Serwis odpowiedzialny za generowanie i aktualizację plików tekstowych z kolejką programów dla maszyn.
 */
@Service
public class MachineQueueFileGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(MachineQueueFileGeneratorService.class);

    private final MachineRepository machineRepository;
    private final ProductionQueueItemRepository productionQueueItemRepository;

    public MachineQueueFileGeneratorService(
            MachineRepository machineRepository,
            ProductionQueueItemRepository productionQueueItemRepository) {
        this.machineRepository = machineRepository;
        this.productionQueueItemRepository = productionQueueItemRepository;
    }

    /**
     * Generuje plik tekstowy z listą programów dla danej maszyny w formacie:
     * "pozycja./orderName/partName/załącznik id: ID | [status]"
     * Ilość jest dodawana w nagłówku programu w formacie: "Ilość: ilość szt"
     * Jeśli istnieje additionalInfo, jest dodawane w osobnej linii przed programem w formacie: "/** Uwagi: additionalInfo "
     * Jeśli istnieje author, jest dodawane w formacie: "Autor: sanitized_author"
     * Długie additionalInfo są dzielone na linie po maksymalnie 80 znaków.
     * Informacje o przygotówce są dodawane w jednej linii w formacie:
     * - Płyta: "<materialTypeName> | Płyta | <x> x <y> x <z> mm"
     * - Rura: "<materialTypeName> | Rura | ∅<diameter> x ∅<innerDiameter> x <length> mm"
     * - Pręt: "<materialTypeName> | Pręt | ∅<diameter> x <length> mm"
     * Jeśli przygotówka jest niezdefiniowana (brak danych lub wymiary = 0/null), linia przygotówki jest pomijana.
     * Tylko załączniki z rozszerzeniem .MPF są uwzględniane.
     * Programy są sortowane według pola 'order', a załączniki według pola 'order'.
     * Status załącznika to [OK] lub [NOK], oddzielony znakiem '|'.
     * orderName, partName i mpfFileName nie są skracane.
     * Plik zawiera instrukcje, datę generowania, nagłówki programów, separatory między programami i informację o pustej kolejce.
     *
     * @param queueType ID maszyny (jako String)
     * @throws IOException jeśli operacja na pliku się nie powiedzie
     */
    public void generateQueueFileForMachine(String queueType) throws IOException {

        logger.info("Generowanie pliku kolejki dla queueType: {}", queueType);
        if (queueType == null || "ncQueue".equals(queueType) || "completed".equals(queueType)) {
            return;
        }

        try {
            Integer machineId = Integer.parseInt(queueType);
            Optional<Machine> machineOpt = machineRepository.findById(machineId);
            if (machineOpt.isEmpty()) {
                return;
            }

            Machine machine = machineOpt.get();
            String fileName = machine.getMachineName() + ".txt";
            String cleanedPath = machine.getQueueFilePath().replaceFirst("^/+", "").replaceFirst("^cnc/?", "");
            String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local";
            Path mountDir = "prod".equalsIgnoreCase(appEnv) || "docker-local".equalsIgnoreCase(appEnv)
                    ? Paths.get("/cnc")
                    : Paths.get("./cnc");
            Path resolvedPath = cleanedPath.isEmpty() ? mountDir : mountDir.resolve(cleanedPath).normalize();
            Path filePath = resolvedPath.resolve(fileName);

            Files.createDirectories(filePath.getParent());

            List<ProductionQueueItem> programs = productionQueueItemRepository.findByQueueType(queueType, Pageable.unpaged()).getContent()
                    .stream()
                    .sorted(Comparator.comparing(ProductionQueueItem::getOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());

            System.out.println("Pobrane programy dla queueType " + queueType + ":");
            programs.forEach(program -> System.out.println("ID: " + program.getId() + ", orderName: " + program.getOrderName() + ", partName: " + program.getPartName() + ", order: " + program.getOrder()));

            StringBuilder content = new StringBuilder();
            content.append("# Edytuj tylko statusy w nawiasach: [OK] lub [NOK].\n");
            content.append("# Przykład: zmień '[NOK]' na '[OK]'. Nie zmieniaj ID, nazw ani innych danych!\n");
            content.append("# Ścieżka /orderName/partName/załącznik wskazuje lokalizację programu.\n");
            content.append("# Błędy w formacie linii mogą zostać zignorowane przez system.\n");
            content.append(String.format("# Wygenerowano: %s\n\n",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

            int position = 1;
            String lastPartName = null;
            Integer lastProgramId = null;

            for (ProductionQueueItem program : programs) {
                List<ProductionFileInfo> mpfFiles = program.getFiles() != null ?
                        program.getFiles().stream()
                                .filter(file -> file.getFileName().toLowerCase().endsWith(".mpf"))
                                .sorted(Comparator.comparing(ProductionFileInfo::getOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                                .collect(Collectors.toList()) :
                        List.of();

                if (!mpfFiles.isEmpty()) {
                    // Wersje "surowe" dla nagłówka
                    String rawOrderName = program.getOrderName() != null ? program.getOrderName() : "";
                    String rawPartName = program.getPartName() != null ? program.getPartName() : "NoPartName_" + program.getId();
                    String additionalInfo = program.getAdditionalInfo() != null ? program.getAdditionalInfo() : "";
                    String author = program.getAuthor() != null ? program.getAuthor() : "";

                    // Wersje "oczyszczone" dla ścieżek
                    String sanitizedOrderName = sanitizeFileName(program.getOrderName(), "NoOrderName_" + program.getId());
                    String sanitizedPartName = sanitizeFileName(program.getPartName(), "NoPartName_" + program.getId());

                    int quantity = program.getQuantity();

                    String preparationInfo = buildPreparationInfoString(program);

                    System.out.println("Generowanie dla ID: " + program.getId() + ", partName: " + rawPartName + ", order: " + program.getOrder());

                    if (lastProgramId != null && !lastProgramId.equals(program.getId())) {
                        content.append("\n---\n\n");
                    }

                    content.append("/**\n");
                    content.append(String.format("Zamówienie: %s\n", rawOrderName));
                    content.append(String.format("Nazwa elementu: %s\n", rawPartName));
                    if (!author.isEmpty()) {
                        content.append(String.format("Autor: %s\n", author));
                    }
                    content.append(String.format("Ilość: %d szt\n", quantity));
                    if (!preparationInfo.isEmpty()) {
                        content.append(String.format("Przygotówka: %s\n", preparationInfo));
                    }
                    if (!additionalInfo.isEmpty()) {
                        content.append(wrapCommentWithPrefix(additionalInfo, "Uwagi: "));
                    }
                    content.append(" */\n");
                    content.append("\n");

                    if (lastPartName != null && !rawPartName.equals(lastPartName) && lastProgramId != null && lastProgramId.equals(program.getId())) {
                        content.append("\n");
                    }

                    for (ProductionFileInfo mpfFile : mpfFiles) {
                        boolean isFileCompleted = mpfFile.isCompleted();
                        String status = isFileCompleted ? "[OK]" : "[NOK]";
                        String mpfFileName = sanitizeFileName(mpfFile.getFileName(), "NoFileName_" + mpfFile.getId());
                        String entry = String.format("%d./%s/%s/%s id: %d | %s\n",
                                position++,
                                sanitizedOrderName,
                                sanitizedPartName,
                                mpfFileName,
                                program.getId(),
                                status);
                        content.append(entry);
                    }

                    lastPartName = rawPartName;
                    lastProgramId = program.getId();
                }
            }

            // NOWA, POPRAWIONA WERSJA
            boolean programsAdded = programs.stream().anyMatch(p -> p.getFiles() != null && !p.getFiles().isEmpty() && p.getFiles().stream().anyMatch(f -> f.getFileName().toLowerCase().endsWith(".mpf")));

            if (programsAdded) {
                Files.writeString(filePath, content.toString());
            } else {
                // Ta jedna linia zastępuje całą logikę usuwania i tworzenia.
                // Utworzy nowy plik lub nadpisze istniejący, eliminując błędy.
                Files.writeString(filePath,
                        "# Edytuj tylko statusy w nawiasach: [OK] lub [NOK].\n" +
                                "# Przykład: zmień '[NOK]' na '[OK]'. Nie zmieniaj ID, nazw ani innych danych!\n" +
                                "# Ścieżka /orderName/partName/załącznik wskazuje lokalizację programu.\n" +
                                "# Błędy w formacie linii mogą zostać zignorowane przez system.\n" +
                                String.format("# Wygenerowano: %s\n",
                                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))) +
                                "# Brak programów w kolejce dla tej maszyny.\n");
            }
        } catch (NumberFormatException e) {
            // Ignoruj, jeśli queueType nie jest liczbą
        }
    }

    private String sanitizeFileName(String name, String defaultName) {
        if (name == null || name.trim().isEmpty()) {
            return defaultName;
        }
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        normalized = normalized.replaceAll("[ąĄ]", "a")
                .replaceAll("[ćĆ]", "c")
                .replaceAll("[ęĘ]", "e")
                .replaceAll("[łŁ]", "l")
                .replaceAll("[ńŃ]", "n")
                .replaceAll("[óÓ]", "o")
                .replaceAll("[śŚ]", "s")
                .replaceAll("[źŹ]", "z")
                .replaceAll("[żŻ]", "z");
        return normalized.replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_");
    }

    private String wrapCommentWithPrefix(String comment, String prefix) {
        final int MAX_LINE_LENGTH = 80;
        StringBuilder wrapped = new StringBuilder();
        String[] words = comment.split("\\s+");
        StringBuilder currentLine = new StringBuilder(prefix);
        boolean firstLine = true;

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > MAX_LINE_LENGTH) {
                wrapped.append(currentLine.toString().trim()).append("\n");
                currentLine = new StringBuilder(firstLine ? prefix : "");
                firstLine = false;
            }
            currentLine.append(word).append(" ");
        }

        if (currentLine.length() > (firstLine ? prefix.length() : 0)) {
            wrapped.append(currentLine.toString().trim()).append("\n");
        }

        return wrapped.toString();
    }

    private String buildPreparationInfoString(ProductionQueueItem program) {
        String materialTypeName = program.getMaterialType() != null && program.getMaterialType().getName() != null ?
                program.getMaterialType().getName() : "Brak";
        String materialProfile = program.getMaterialProfile() != null && !program.getMaterialProfile().isEmpty() ?
                translateMaterialProfile(program.getMaterialProfile()) : "Brak";
        boolean hasValidMaterialData = !"Brak".equals(materialTypeName) && !"Brak".equals(materialProfile);

        StringBuilder dimensions = new StringBuilder();
        boolean hasValidDimensions = false;

        if ("Płyta".equals(materialProfile)) {
            boolean hasX = program.getX() != null && program.getX() > 0;
            boolean hasY = program.getY() != null && program.getY() > 0;
            boolean hasZ = program.getZ() != null && program.getZ() > 0;
            if (hasX || hasY || hasZ) {
                dimensions.append(String.format(Locale.US, "%.2f", hasX ? program.getX() : 0.0));
                dimensions.append(" x ");
                dimensions.append(String.format(Locale.US, "%.2f", hasY ? program.getY() : 0.0));
                dimensions.append(" x ");
                dimensions.append(String.format(Locale.US, "%.2f", hasZ ? program.getZ() : 0.0));
                dimensions.append(" mm");
                hasValidDimensions = true;
            }
        } else if ("Rura".equals(materialProfile)) {
            boolean hasDiameter = program.getDiameter() != null && program.getDiameter() > 0;
            boolean hasInnerDiameter = program.getInnerDiameter() != null && program.getInnerDiameter() > 0;
            boolean hasLength = program.getLength() != null && program.getLength() > 0;
            if (hasDiameter || hasInnerDiameter || hasLength) {
                dimensions.append(String.format(Locale.US, "∅%.2f", hasDiameter ? program.getDiameter() : 0.0));
                dimensions.append(" x ");
                dimensions.append(String.format(Locale.US, "∅%.2f", hasInnerDiameter ? program.getInnerDiameter() : 0.0));
                dimensions.append(" x ");
                dimensions.append(String.format(Locale.US, "%.2f", hasLength ? program.getLength() : 0.0));
                dimensions.append(" mm");
                hasValidDimensions = true;
            }
        } else if ("Pręt".equals(materialProfile)) {
            boolean hasDiameter = program.getDiameter() != null && program.getDiameter() > 0;
            boolean hasLength = program.getLength() != null && program.getLength() > 0;
            if (hasDiameter || hasLength) {
                dimensions.append(String.format(Locale.US, "∅%.2f", hasDiameter ? program.getDiameter() : 0.0));
                dimensions.append(" x ");
                dimensions.append(String.format(Locale.US, "%.2f", hasLength ? program.getLength() : 0.0));
                dimensions.append(" mm");
                hasValidDimensions = true;
            }
        }

        if (!hasValidMaterialData || !hasValidDimensions) {
            return "";
        }
        return String.format("%s | %s | %s", materialTypeName, materialProfile, dimensions.toString());
    }

    private String translateMaterialProfile(String materialProfile) {
        switch (materialProfile) {
            case "Plate":
                return "Płyta";
            case "Tube":
                return "Rura";
            case "Rod":
                return "Pręt";
            default:
                return materialProfile != null ? materialProfile : "Brak";
        }
    }
}