package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileImage.FileImage;
import com.example.infraboxapi.FileImage.FileImageService;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class MachineService {

    private static final Logger logger = LoggerFactory.getLogger(MachineService.class);

    private final MachineRepository machineRepository;
    private final FileImageService fileImageService;
    private final ProductionQueueItemService productionQueueItemService;
    private final MachineQueueFileGeneratorService machineQueueFileGeneratorService;

    public MachineService(
            MachineRepository machineRepository,
            FileImageService fileImageService,
            ProductionQueueItemService productionQueueItemService,
            MachineQueueFileGeneratorService machineQueueFileGeneratorService) {
        this.machineRepository = machineRepository;
        this.fileImageService = fileImageService;
        this.productionQueueItemService = productionQueueItemService;
        this.machineQueueFileGeneratorService = machineQueueFileGeneratorService;
    }

    @Transactional
    public Machine createMachine(MachineRequest request, MultipartFile imageFile) throws IOException {
        // Sprawdź, czy machineName już istnieje
        if (machineRepository.existsByMachineName(request.getMachineName())) {
            throw new IllegalArgumentException("Machine name '" + request.getMachineName() + "' already exists");
        }

        FileImage image = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            image = fileImageService.createFile(imageFile);
        }

        Machine machine = Machine.builder()
                .machineName(request.getMachineName())
                .image(image)
                .programPath(request.getProgramPath())
                .queueFilePath(request.getQueueFilePath())
                .build();

        // Zapisz maszynę w bazie danych
        Machine savedMachine = machineRepository.save(machine);
        logger.info("Utworzono maszynę o ID: {} i nazwie: {}", savedMachine.getId(), savedMachine.getMachineName());

        // Utwórz pusty plik kolejki
        String fileName = savedMachine.getMachineName() + ".txt";
        Path filePath = Paths.get(savedMachine.getQueueFilePath(), fileName);
        try {
            // Utwórz katalogi nadrzędne, jeśli nie istnieją
            Files.createDirectories(filePath.getParent());
            // Generuj treść pustego pliku kolejki
            StringBuilder content = new StringBuilder();
            content.append("# Edytuj tylko statusy w nawiasach: [UKONCZONE] lub [NIEUKONCZONE].\n");
            content.append("# Przyklad: zmień '[NIEUKONCZONE]' na '[UKONCZONE]'. Nie zmieniaj ID, nazw ani innych danych!\n");
            content.append("# Sciezka /[orderName]/[partName]/załącznik wskazuje lokalizację programu na dysku maszyny.\n");
            content.append("# Bledy w formacie linii moga zostac zignorowane przez system.\n");
            content.append(String.format("# Wygenerowano: %s\n",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            content.append("# Brak programow w kolejce dla tej maszyny.\n");

            // Zapisz plik
            Files.writeString(filePath, content.toString());
            logger.info("Utworzono pusty plik kolejki: {}", filePath);
        } catch (IOException e) {
            logger.error("Błąd podczas tworzenia pliku kolejki: {}", filePath, e);
            throw new IOException("Nie udało się utworzyć pliku kolejki dla maszyny: " + filePath, e);
        }

        return savedMachine;
    }

    public Optional<Machine> findById(Integer id) {
        return machineRepository.findById(id);
    }

    public List<Machine> findAll() {
        return machineRepository.findAll();
    }

    @Transactional
    public Machine updateMachine(Integer id, MachineRequest request, MultipartFile imageFile) throws IOException {
        Optional<Machine> existingMachineOpt = machineRepository.findById(id);
        if (existingMachineOpt.isEmpty()) {
            throw new RuntimeException("Machine with ID " + id + " not found");
        }

        Machine existingMachine = existingMachineOpt.get();
        String oldMachineName = existingMachine.getMachineName(); // Zachowaj starą nazwę do zmiany pliku kolejki

        // Sprawdź unikalność machineName, pomijając bieżącą maszynę
        if (!oldMachineName.equals(request.getMachineName()) &&
                machineRepository.existsByMachineName(request.getMachineName())) {
            throw new IllegalArgumentException("Machine name '" + request.getMachineName() + "' already exists");
        }

        // Zaktualizuj dane maszyny
        existingMachine.setMachineName(request.getMachineName());
        existingMachine.setProgramPath(request.getProgramPath());
        existingMachine.setQueueFilePath(request.getQueueFilePath());

        // Jeśli zmieniono nazwę maszyny, zaktualizuj plik kolejki
        if (!oldMachineName.equals(request.getMachineName())) {
            // Usuń stary plik kolejki
            String oldFileName = oldMachineName + ".txt";
            Path oldFilePath = Paths.get(existingMachine.getQueueFilePath(), oldFileName);
            try {
                if (Files.exists(oldFilePath)) {
                    Files.delete(oldFilePath);
                    logger.info("Deleted old queue file: {}", oldFilePath);
                }
            } catch (IOException e) {
                logger.error("Error deleting old queue file {}: {}", oldFilePath, e.getMessage(), e);
                // Kontynuuj, nawet jeśli plik nie mógł zostać usunięty
            }

            // Wygeneruj nowy plik kolejki z nową nazwą
            try {
                machineQueueFileGeneratorService.generateQueueFileForMachine(String.valueOf(id));
                logger.info("Generated new queue file for updated machine name: {}", request.getMachineName());
            } catch (IOException e) {
                logger.error("Error generating new queue file for machine ID {}: {}", id, e.getMessage(), e);
                throw new IOException("Failed to generate new queue file after renaming machine", e);
            }
        }

        // Zaktualizuj obraz, jeśli przesłano nowy
        if (imageFile != null && !imageFile.isEmpty()) {
            FileImage newImage = fileImageService.updateFile(imageFile, existingMachine.getImage());
            existingMachine.setImage(newImage);
        }

        return machineRepository.save(existingMachine);
    }

    @Transactional
    public void deleteById(Integer id) {
        Optional<Machine> machineOpt = machineRepository.findById(id);
        if (machineOpt.isEmpty()) {
            logger.warn("Próba usunięcia nieistniejącej maszyny o ID: {}", id);
            return;
        }

        Machine machine = machineOpt.get();
        // Usuń plik kolejki (machineName.txt)
        String fileName = machine.getMachineName() + ".txt";
        Path filePath = Paths.get(machine.getQueueFilePath(), fileName);
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                logger.info("Usunięto plik kolejki: {}", filePath);
            } else {
                logger.info("Plik kolejki nie istnieje: {}", filePath);
            }
        } catch (IOException e) {
            logger.error("Błąd podczas usuwania pliku kolejki: {}", filePath, e);
            // Kontynuuj usuwanie maszyny, nawet jeśli plik nie mógł zostać usunięty
        }

        // Usuń maszynę
        machineRepository.deleteById(id);
        logger.info("Maszyna o ID: {} została usunięta", id);
    }

    public List<String> getAvailableLocations() {
        String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local";
        File mountDir;

        if ("prod".equalsIgnoreCase(appEnv)) {
            mountDir = new File("/cnc");
        } else if ("docker-local".equalsIgnoreCase(appEnv)) {
            mountDir = new File("/cnc");
        } else {
            mountDir = new File("./cnc");
        }

        List<String> locations = new java.util.ArrayList<>();

        // Sprawdź, czy główny katalog istnieje i jest dostępny
        if (mountDir.exists() && mountDir.isDirectory() && mountDir.canRead() && mountDir.canWrite()) {
            // Pobierz bezpośrednie podkatalogi w mountDir (pierwszy poziom)
            File[] firstLevelDirs = mountDir.listFiles(File::isDirectory);
            if (firstLevelDirs != null && firstLevelDirs.length > 0) {
                for (File firstLevelDir : firstLevelDirs) {
                    if (firstLevelDir.canRead() && firstLevelDir.canWrite()) {
                        // Dodaj katalog pierwszego poziomu
                        locations.add(firstLevelDir.getAbsolutePath());

                        // Pobierz podkatalogi drugiego poziomu
                        File[] secondLevelDirs = firstLevelDir.listFiles(File::isDirectory);
                        if (secondLevelDirs != null && secondLevelDirs.length > 0) {
                            List<String> secondLevelPaths = Arrays.stream(secondLevelDirs)
                                    .filter(dir -> dir.canRead() && dir.canWrite())
                                    .map(File::getAbsolutePath)
                                    .collect(Collectors.toList());
                            locations.addAll(secondLevelPaths);
                        }
                    }
                }
            }
        }

        return locations;
    }

    public ResponseEntity<byte[]> downloadMachinePrograms(Integer machineId) throws IOException {
        Optional<Machine> machineOpt = findById(machineId);
        if (machineOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Machine machine = machineOpt.get();
        List<ProductionQueueItem> programs = productionQueueItemService.findByQueueType(String.valueOf(machineId));

        if (programs.isEmpty() || programs.stream().noneMatch(program -> !program.getFiles().isEmpty())) {
            return ResponseEntity.noContent().build();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (ProductionQueueItem program : programs) {
                String orderName = program.getOrderName() != null && !program.getOrderName().isEmpty()
                        ? program.getOrderName()
                        : "NoOrderName_" + program.getId();
                orderName = orderName.replaceAll("[^a-zA-Z0-9_\\-]", "_"); // Sanitize folder name

                String partName = program.getPartName() != null && !program.getPartName().isEmpty()
                        ? program.getPartName()
                        : "NoPartName_" + program.getId();
                partName = partName.replaceAll("[^a-zA-Z0-9_\\-]", "_"); // Sanitize part name

                for (ProductionFileInfo file : program.getFiles()) {
                    String fileName = file.getFileName();
                    // Nowy wzór: [machineName]/[orderName]/[partName]/[załączniki]
                    String entryPath = String.format("%s/%s/%s/%s", machine.getMachineName(), orderName, partName, fileName);
                    entryPath = entryPath.replaceAll("[^a-zA-Z0-9_\\-/\\.]", "_"); // Sanitize path

                    ZipEntry zipEntry = new ZipEntry(entryPath);
                    zos.putNextEntry(zipEntry);
                    zos.write(file.getFileContent());
                    zos.closeEntry();
                }
            }
            zos.finish();
        }

        byte[] zipBytes = baos.toByteArray();
        String zipFileName = machine.getMachineName() + ".zip";
        zipFileName = zipFileName.replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_"); // Sanitize filename

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipBytes);
    }
}