package com.example.infraboxapi.materialType;

import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class MaterialTypeService {

    private final MaterialTypeRepository materialTypeRepository;
    private final NotificationService notificationService;

    public void createMaterialType(MaterialTypeDTO materialTypeDTO) {

        MaterialType materialType = MaterialType.builder()
                .name(materialTypeDTO.getName())
                .density(materialTypeDTO.getDensity())
                .build();

        materialTypeRepository.save(materialType);

        notificationService.createAndSendNotification("Material type " + materialType.getName() + " created", NotificationDescription.MaterialTypeAdded);
    }

    public List<MaterialType> getAllMaterials() {
        return materialTypeRepository.findAll();
    }

    public void deleteMaterialType(Integer id) {
        MaterialType materialType = materialTypeRepository.findById(id).orElseThrow(() -> new RuntimeException("Material type not found"));

        materialTypeRepository.deleteById(id);
        notificationService.createAndSendNotification("Material type " + materialType.getName() + " deleted", NotificationDescription.MaterialTypeDeleted);
    }

    public void updateMaterialType(MaterialTypeDTO materialTypeDTO) {
        MaterialType materialType = materialTypeRepository.findById(materialTypeDTO.getId()).orElseThrow(() -> new RuntimeException("Material type not found"));
        materialType.setName(materialTypeDTO.getName());
        materialType.setDensity(materialTypeDTO.getDensity());
        materialTypeRepository.save(materialType);
        notificationService.createAndSendNotification("Material type " + materialType.getName() + " updated", NotificationDescription.MaterialTypeUpdated);

    }
}
