package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileImage.FileImage;
import com.example.infraboxapi.FileImage.FileImageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MachineService {

    private final MachineRepository machineRepository;
    private final FileImageService fileImageService;

    public MachineService(MachineRepository machineRepository, FileImageService fileImageService) {
        this.machineRepository = machineRepository;
        this.fileImageService = fileImageService;
    }

    @Transactional
    public Machine createMachine(MachineRequest request, MultipartFile imageFile) throws IOException {
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

        return machineRepository.save(machine);
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
        existingMachine.setMachineName(request.getMachineName());
        existingMachine.setProgramPath(request.getProgramPath());
        existingMachine.setQueueFilePath(request.getQueueFilePath());

        if (imageFile != null && !imageFile.isEmpty()) {
            FileImage newImage = fileImageService.updateFile(imageFile, existingMachine.getImage());
            existingMachine.setImage(newImage);
        }

        return machineRepository.save(existingMachine);
    }

    @Transactional
    public void deleteById(Integer id) {
        machineRepository.deleteById(id);
    }
    public List<String> getAvailableLocations() {
        String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local";
        File mountDir;

        // Określenie katalogu cnc na podstawie środowiska
        if ("prod".equalsIgnoreCase(appEnv)) {
            mountDir = new File("/mnt/cnc");
        } else if ("docker-local".equalsIgnoreCase(appEnv)) {
            mountDir = new File("/cnc");
        } else {
            mountDir = new File("./cnc");
        }

        // Inicjalizacja listy lokalizacji
        List<String> locations = new java.util.ArrayList<>();

        // Sprawdzenie, czy katalog cnc istnieje i ma odpowiednie uprawnienia
        if (mountDir.exists() && mountDir.isDirectory() && mountDir.canRead() && mountDir.canWrite()) {
            // Dodanie katalogu cnc jako opcji
            locations.add(mountDir.getAbsolutePath());

            // Pobranie podkatalogów
            File[] subDirs = mountDir.listFiles(File::isDirectory);
            if (subDirs != null && subDirs.length > 0) {
                // Dodanie podkatalogów z uprawnieniami
                List<String> subDirPaths = Arrays.stream(subDirs)
                        .filter(dir -> dir.canRead() && dir.canWrite())
                        .map(File::getAbsolutePath)
                        .collect(Collectors.toList());
                locations.addAll(subDirPaths);
            }
        }

        return locations;
    }
}