package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileImage.FileImage;
import com.example.infraboxapi.FileImage.FileImageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

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
}