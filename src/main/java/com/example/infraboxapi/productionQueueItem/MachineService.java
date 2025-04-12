package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileImage.FileImage;
import com.example.infraboxapi.FileImage.FileImageService;
import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class MachineService {

    private final MachineRepository machineRepository;
    private final FileImageService fileImageService;
    private final ProductionQueueItemService productionQueueItemService;

    public MachineService(
            MachineRepository machineRepository,
            FileImageService fileImageService,
            ProductionQueueItemService productionQueueItemService) {
        this.machineRepository = machineRepository;
        this.fileImageService = fileImageService;
        this.productionQueueItemService = productionQueueItemService;
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

        if ("prod".equalsIgnoreCase(appEnv)) {
            mountDir = new File("/mnt/cnc");
        } else if ("docker-local".equalsIgnoreCase(appEnv)) {
            mountDir = new File("/cnc");
        } else {
            mountDir = new File("./cnc");
        }

        List<String> locations = new java.util.ArrayList<>();

        if (mountDir.exists() && mountDir.isDirectory() && mountDir.canRead() && mountDir.canWrite()) {
            locations.add(mountDir.getAbsolutePath());

            File[] subDirs = mountDir.listFiles(File::isDirectory);
            if (subDirs != null && subDirs.length > 0) {
                List<String> subDirPaths = Arrays.stream(subDirs)
                        .filter(dir -> dir.canRead() && dir.canWrite())
                        .map(File::getAbsolutePath)
                        .collect(Collectors.toList());
                locations.addAll(subDirPaths);
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