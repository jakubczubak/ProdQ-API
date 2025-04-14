package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
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
     * "pozycja. orderName/partName/załącznik - ilość szt. [- dodatkowe info] id: ID [status]"
     * Tylko załączniki z rozszerzeniem .MPF są uwzględniane.
     * Programy są sortowane według pola 'order', a załączniki alfabetycznie według nazwy.
     * Wpisy dla różnych partName są oddzielone podwójnym enterem.
     * Status załącznika to [Ukonczone] lub [Nieukonczone], w zależności od pola completed w ProductionFileInfo.
     * Wszystkie nazwy są sanitizowane, aby usunąć polskie znaki.
     * Plik zaczyna się od komentarza z instrukcją dla operatorów.
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
            // Dodaj komentarz na początku pliku
            content.append("# Edytuj tylko statusy [Ukonczone]/[Nieukonczone]. Zachowaj format linii!\n\n");
            int position = 1;
            String lastPartName = null;

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

                    // Dodaj podwójny enter, jeśli partName się zmienił
                    if (lastPartName != null && !partName.equals(lastPartName)) {
                        content.append("\n\n");
                    }

                    for (ProductionFileInfo mpfFile : mpfFiles) {
                        boolean isFileCompleted = mpfFile.isCompleted();
                        String status = isFileCompleted ? "[Ukonczone]" : "[Nieukonczone]";
                        String mpfFileName = sanitizeFileName(mpfFile.getFileName(), "NoFileName_" + mpfFile.getId());
                        // Format: pozycja. orderName/partName/załącznik - ilość szt. [- dodatkowe info] id: ID [status]
                        String entry = String.format("%d. %s/%s/%s - %d szt.%s id: %d %s\n",
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
                }
            }

            // Zapisz plik lub utwórz pusty
            if (content.length() > 0) {
                Files.writeString(filePath, content.toString());
            } else {
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }
                Files.createFile(filePath);
                // Utwórz pusty plik z komentarzem
                Files.writeString(filePath, "# Edytuj tylko statusy [Ukonczone]/[Nieukonczone]. Zachowaj format linii!\n");
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