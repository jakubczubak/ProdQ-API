package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        // Pomiń dla ncQueue i completed
        if (queueType == null || "ncQueue".equals(queueType) || "completed".equals(queueType)) {
            return;
        }

        try {
            Integer machineId = Integer.parseInt(queueType);
            Optional<Machine> machineOpt = machineRepository.findById(machineId);
            if (machineOpt.isEmpty()) {
                return; // Maszyna nie istnieje, pomiń
            }

            Machine machine = machineOpt.get();
            String fileName = machine.getMachineName() + ".txt";
            // Normalizuj queueFilePath i rozwiąż względem katalogu głównego
            String cleanedPath = machine.getQueueFilePath().replaceFirst("^/+", "").replaceFirst("^cnc/?", "");
            String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local";
            Path mountDir = "prod".equalsIgnoreCase(appEnv) || "docker-local".equalsIgnoreCase(appEnv)
                    ? Paths.get("/cnc")
                    : Paths.get("./cnc");
            Path resolvedPath = cleanedPath.isEmpty() ? mountDir : mountDir.resolve(cleanedPath).normalize();
            Path filePath = resolvedPath.resolve(fileName);

            // Utwórz katalogi nadrzędne
            Files.createDirectories(filePath.getParent());

            // Pobierz programy dla maszyny i posortuj według pola 'order'
            List<ProductionQueueItem> programs = productionQueueItemRepository.findByQueueType(queueType)
                    .stream()
                    .sorted(Comparator.comparing(ProductionQueueItem::getOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());

            // Debugowanie: wyświetl pobrane programy
            System.out.println("Pobrane programy dla queueType " + queueType + ":");
            programs.forEach(program -> System.out.println("ID: " + program.getId() + ", orderName: " + program.getOrderName() + ", partName: " + program.getPartName() + ", order: " + program.getOrder()));

            // Generuj treść pliku
            StringBuilder content = new StringBuilder();
            // Dodaj komentarz i datę generowania przed listą programów
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
                // Filtruj tylko pliki .MPF i posortuj według pola 'order'
                List<ProductionFileInfo> mpfFiles = program.getFiles() != null ?
                        program.getFiles().stream()
                                .filter(file -> file.getFileName().toLowerCase().endsWith(".mpf"))
                                .sorted(Comparator.comparing(ProductionFileInfo::getOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                                .collect(Collectors.toList()) :
                        List.of();

                if (!mpfFiles.isEmpty()) {
                    String orderName = sanitizeFileName(program.getOrderName(), "NoOrderName_" + program.getId());
                    String partName = program.getPartName() != null && !program.getPartName().isEmpty() ?
                            program.getPartName() : "NoPartName_" + program.getId();
                    String additionalInfo = program.getAdditionalInfo() != null && !program.getAdditionalInfo().isEmpty() ?
                            sanitizeFileName(program.getAdditionalInfo(), "") : "";
                    String author = program.getAuthor() != null && !program.getAuthor().isEmpty() ?
                            sanitizeFileName(program.getAuthor(), "") : "";
                    int quantity = program.getQuantity();

                    // Pobierz dane o przygotówce w jednej linii
                    String preparationInfo = buildPreparationInfoString(program);

                    // Debugowanie: wyświetl użyte partName i order
                    System.out.println("Generowanie dla ID: " + program.getId() + ", partName: " + partName + ", order: " + program.getOrder());

                    // Dodaj separator dla nowego programu (różne ID)
                    if (lastProgramId != null && !lastProgramId.equals(program.getId())) {
                        content.append("\n---\n\n");
                    }

                    // Dodaj nagłówek programu
                    content.append("/**\n");
                    content.append(String.format("Zamówienie: %s\n", orderName)); // Nowa linia: Zamówienie
                    content.append(String.format("Nazwa elementu: %s\n", partName)); // Nowa linia: Nazwa elementu
                    if (!author.isEmpty()) {
                        content.append(String.format("Autor: %s\n", author));
                    }
                    content.append(String.format("Ilość: %d szt\n", quantity));
                    // Dodaj linię przygotówki tylko, jeśli nie jest pusta
                    if (!preparationInfo.isEmpty()) {
                        content.append(String.format("Przygotówka: %s\n", preparationInfo));
                    }
                    if (!additionalInfo.isEmpty()) {
                        content.append(wrapCommentWithPrefix(additionalInfo, "Uwagi: "));
                    }
                    content.append(" */\n");
                    content.append("\n"); // Dodaj enter po sekcji /** ... */

                    // Dodaj pustą linię, jeśli partName się zmienił w obrębie tego samego programu
                    if (lastPartName != null && !partName.equals(lastPartName) && lastProgramId != null && lastProgramId.equals(program.getId())) {
                        content.append("\n");
                    }

                    for (ProductionFileInfo mpfFile : mpfFiles) {
                        boolean isFileCompleted = mpfFile.isCompleted();
                        String status = isFileCompleted ? "[OK]" : "[NOK]";
                        String mpfFileName = sanitizeFileName(mpfFile.getFileName(), "NoFileName_" + mpfFile.getId());
                        // Format: pozycja./orderName/partName/załącznik id: ID | [status]
                        String entry = String.format("%d./%s/%s/%s id: %d | %s\n",
                                position++,
                                orderName,
                                partName,
                                mpfFileName,
                                program.getId(),
                                status);
                        content.append(entry);
                    }

                    lastPartName = partName;
                    lastProgramId = program.getId();
                }
            }

            // Zapisz plik lub utwórz pusty
            if (content.toString().trim().length() > content.indexOf("\n\n") + 2) {
                Files.writeString(filePath, content.toString());
            } else {
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }
                Files.createFile(filePath);
                // Utwórz pusty plik z komentarzem i informacją
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
            // queueType nie jest ID maszyny, pomiń
        }
    }

    /**
     * Sanitizuje nazwę, usuwając polskie znaki, niedozwolone znaki i zwracając domyślną wartość,
     * jeśli nazwa jest null lub pusta.
     *
     * @param name nazwa do sanitizacji
     * @param defaultName domyślna nazwa w razie null/pustej wartości
     * @return sanitizowana nazwa
     */
    private String sanitizeFileName(String name, String defaultName) {
        if (name == null || name.trim().isEmpty()) {
            return defaultName;
        }
        // Normalizuj znaki i usuń polskie diakrytyki
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        // Zastąp specyficzne polskie znaki
        normalized = normalized.replaceAll("[ąĄ]", "a")
                .replaceAll("[ćĆ]", "c")
                .replaceAll("[ęĘ]", "e")
                .replaceAll("[łŁ]", "l")
                .replaceAll("[ńŃ]", "n")
                .replaceAll("[óÓ]", "o")
                .replaceAll("[śŚ]", "s")
                .replaceAll("[źŹ]", "z")
                .replaceAll("[żŻ]", "z");
        // Usuń niedozwolone znaki
        return normalized.replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_");
    }

    /**
     * Dzieli długi komentarz na linie po maksymalnie 80 znaków, dodając prefiks do każdej linii.
     *
     * @param comment komentarz do podzielenia
     * @param prefix prefiks dla każdej linii (np. "Uwagi: ")
     * @return sformatowany komentarz z enterami
     */
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

    /**
     * Buduje ciąg znaków z informacjami o przygotówce w jednej linii w formacie:
     * - Płyta: "<materialTypeName> | Płyta | <x> x <y> x <z> mm"
     * - Rura: "<materialTypeName> | Rura | ∅<diameter> x ∅<innerDiameter> x <length> mm"
     * - Pręt: "<materialTypeName> | Pręt | ∅<diameter> x <length> mm"
     * Jeśli przygotówka jest niezdefiniowana (brak danych lub wymiary = 0/null), zwraca pusty ciąg.
     * Używa kropek zamiast przecinków w liczbach dziesiętnych.
     *
     * @param program element kolejki produkcyjnej
     * @return sformatowany ciąg informacji o przygotówce lub pusty ciąg, jeśli niezdefiniowana
     */
    private String buildPreparationInfoString(ProductionQueueItem program) {
        // Typ materiału
        String materialTypeName = program.getMaterialType() != null && program.getMaterialType().getName() != null ?
                sanitizeFileName(program.getMaterialType().getName(), "Brak") : "Brak";

        // Profil materiału
        String materialProfile = program.getMaterialProfile() != null && !program.getMaterialProfile().isEmpty() ?
                translateMaterialProfile(program.getMaterialProfile()) : "Brak";

        // Sprawdź, czy istnieją wystarczające dane
        boolean hasValidMaterialData = !"Brak".equals(materialTypeName) && !"Brak".equals(materialProfile);

        // Wymiary w zależności od profilu materiału
        StringBuilder dimensions = new StringBuilder();
        boolean hasValidDimensions = false;

        if ("Płyta".equals(materialProfile)) {
            // Dla Płyty: x, y, z
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
            // Dla Rury: ∅diameter x ∅innerDiameter x length
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
            // Dla Pręta: ∅diameter x length
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

        // Jeśli brak danych materiałowych lub wymiarów, zwróć pusty ciąg
        if (!hasValidMaterialData || !hasValidDimensions) {
            return "";
        }

        // Połącz w jedną linię z separatorem |
        return String.format("%s | %s | %s", materialTypeName, materialProfile, dimensions.toString());
    }

    /**
     * Tłumaczy wartości materialProfile na język polski.
     *
     * @param materialProfile wartość pola materialProfile (Plate, Tube, Rod)
     * @return przetłumaczona nazwa lub oryginalna wartość, jeśli nieznana
     */
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