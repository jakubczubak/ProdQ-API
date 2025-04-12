package com.example.infraboxapi.FileProductionItem;

import com.example.infraboxapi.productionQueueItem.Machine;
import com.example.infraboxapi.productionQueueItem.MachineRepository;
import com.example.infraboxapi.productionQueueItem.ProductionQueueItem;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
public class ProductionFileInfoService {

    private final ProductionFileInfoRepository productionFileInfoRepository;
    private final MachineRepository machineRepository;

    @Autowired
    public ProductionFileInfoService(
            ProductionFileInfoRepository productionFileInfoRepository,
            MachineRepository machineRepository) {
        this.productionFileInfoRepository = productionFileInfoRepository;
        this.machineRepository = machineRepository;
    }

    public ProductionFileInfo save(ProductionFileInfo fileInfo) {
        return productionFileInfoRepository.save(fileInfo);
    }

    public List<ProductionFileInfo> saveAll(List<ProductionFileInfo> fileInfos) {
        return productionFileInfoRepository.saveAll(fileInfos);
    }

    public Optional<ProductionFileInfo> findById(Long id) {
        return productionFileInfoRepository.findById(id);
    }

    public List<ProductionFileInfo> findAll() {
        return productionFileInfoRepository.findAll();
    }

    @Transactional
    public void deleteById(Long id) {
        Optional<ProductionFileInfo> fileOpt = productionFileInfoRepository.findById(id);
        if (fileOpt.isPresent()) {
            ProductionFileInfo file = fileOpt.get();
            ProductionQueueItem item = file.getProductionQueueItem();
            String queueType = item.getQueueType();

            // Usuń plik z dysku, jeśli program jest przypisany do maszyny
            if (queueType != null && !"ncQueue".equals(queueType) && !"completed".equals(queueType)) {
                Optional<Machine> machineOpt = machineRepository.findById(Integer.parseInt(queueType));
                if (machineOpt.isPresent()) {
                    Machine machine = machineOpt.get();
                    String programPath = machine.getProgramPath();
                    String orderName = item.getOrderName().replaceAll("[^a-zA-Z0-9_\\-]", "_");
                    String partName = item.getPartName().replaceAll("[^a-zA-Z0-9_\\-]", "_");
                    String fileName = file.getFileName().replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_");

                    Path filePath = Paths.get(programPath, orderName, partName, fileName);
                    try {
                        if (Files.exists(filePath)) {
                            Files.delete(filePath);
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to delete file from disk: " + filePath + ", error: " + e.getMessage());
                    }
                }
            }

            productionFileInfoRepository.deleteById(id);
        }
    }
}