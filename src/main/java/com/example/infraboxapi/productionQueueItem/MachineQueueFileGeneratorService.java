package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Serwis odpowiedzialny za generowanie i aktualizację plików tekstowych z kolejką programów dla maszyn.
 */
@Service
public class MachineQueueFileGeneratorService {

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
     * "pozycja. orderName/partName/załącznik - ilość szt. [- dodatkowe info] id: ID | [status]"
     * Tylko załączniki z rozszerzeniem .MPF są uwzględniane.
     * Programy są sortowane według pola 'order', a załączniki alfabetycznie według nazwy.
     * Wpisy dla różnych partName są oddzielone podwójnym enterem.
     * Status załącznika to [Ukonczone] lub [Nieukonczone], oddzielony znakiem '|'.
     * Wszystkie nazwy są sanitizowane, aby usunąć polskie znaki i odpowiadać strukturze katalogów na dysku maszyny.
     * Plik zawiera komentarz z instrukcjami, datę generowania, separatory między programami i informację o pustej kolejce.
     *
     * @param queueType ID maszyny (jako String)
     * @throws IOException jeśli operacja na pliku się nie powiedzie
     */
    public void generateQueueFileForMachine(String queueType) throws IOException {
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
            Path filePath = Paths.get(machine.getQueueFilePath(), fileName);

            // Utwórz katalogi nadrzędne
            Files.createDirectories(filePath.getParent());

            // Pobierz programy dla maszyny i posortuj według pola 'order'
            List<ProductionQueueItem> programs = productionQueueItemRepository.findByQueueType(queueType)
                    .stream()
                    .sorted(Comparator.comparing(ProductionQueueItem::getOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());

            // Generuj treść pliku
            StringBuilder content = new StringBuilder();
            // Dodaj komentarz i datę generowania
            content.append("# Edytuj tylko statusy w nawiasach: [Ukonczone] lub [Nieukonczone].\n");
            content.append("# Przyklad: zmień '[Nieukonczone]' na '[Ukonczone]'. Nie zmieniaj ID, nazw ani innych danych!\n");
            content.append("# Sciezka orderName/partName/załącznik wskazuje lokalizację programu na dysku maszyny.\n");
            content.append("# Bledy w formacie linii moga zostac zignorowane przez system.\n");
            content.append(String.format("# Wygenerowano: %s\n\n",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

            int position = 1;
            String lastPartName = null;
            Integer lastProgramId = null;

            for (ProductionQueueItem program : programs) {
                // Filtruj tylko pliki .MPF i posortuj alfabetycznie
                List<ProductionFileInfo> mpfFiles = program.getFiles().stream()
                        .filter(file -> file.getFileName().toLowerCase().endsWith(".mpf"))
                        .sorted(Comparator.comparing(ProductionFileInfo::getFileName, String.CASE_INSENSITIVE_ORDER))
                        .collect(Collectors.toList());

                if (!mpfFiles.isEmpty()) {
                    String orderName = sanitizeFileName(program.getOrderName(), "NoOrderName_" + program.getId());
                    String partName = sanitizeFileName(program.getPartName(), "NoPartName_" + program.getId());
                    String additionalInfo = program.getAdditionalInfo() != null && !program.getAdditionalInfo().isEmpty() ? sanitizeFileName(program.getAdditionalInfo(), "") : "";
                    int quantity = program.getQuantity();

                    // Dodaj separator między programami (różne ID)
                    if (lastProgramId != null && !lastProgramId.equals(program.getId())) {
                        content.append("---\n");
                    }
                    // Dodaj podwójny enter, jeśli partName się zmienił w obrębie tego samego programu
                    else if (lastPartName != null && !partName.equals(lastPartName)) {
                        content.append("\n\n");
                    }

                    for (ProductionFileInfo mpfFile : mpfFiles) {
                        boolean isFileCompleted = mpfFile.isCompleted();
                        String status = isFileCompleted ? "[Ukonczone]" : "[Nieukonczone]";
                        String mpfFileName = sanitizeFileName(mpfFile.getFileName(), "NoFileName_" + mpfFile.getId());
                        // Format: pozycja. orderName/partName/załącznik - ilość szt. [- dodatkowe info] id: ID | [status]
                        String entry = String.format("%d. %s/%s/%s - %d szt.%s id: %d | %s\n",
                                position++,
                                orderName,
                                partName,
                                mpfFileName,
                                quantity,
                                additionalInfo.isEmpty() ? "" : " - " + additionalInfo,
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
                        "# Edytuj tylko statusy w nawiasach: [Ukonczone] lub [Nieukonczone].\n" +
                                "# Przyklad: zmień '[Nieukonczone]' na '[Ukonczone]'. Nie zmieniaj ID, nazw ani innych danych!\n" +
                                "# Sciezka orderName/partName/załącznik wskazuje lokalizację programu na dysku maszyny.\n" +
                                "# Bledy w formacie linii moga zostac zignorowane przez system.\n" +
                                String.format("# Wygenerowano: %s\n",
                                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))) +
                                "# Brak programow w kolejce dla tej maszyny.\n");
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
     * @return sanitized nazwa
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
}