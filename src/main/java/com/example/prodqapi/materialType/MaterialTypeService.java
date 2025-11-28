package com.example.prodqapi.materialType;

import com.example.prodqapi.notification.NotificationDescription;
import com.example.prodqapi.notification.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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

        notificationService.sendNotification(NotificationDescription.MaterialTypeAdded, Map.of("name", materialType.getName()));
    }

    public List<MaterialType> getAllMaterials() {
        return materialTypeRepository.findAll();
    }

    public void deleteMaterialType(Integer id) {
        MaterialType materialType = materialTypeRepository.findById(id).orElseThrow(() -> new RuntimeException("Material type not found"));

        materialTypeRepository.deleteById(id);
        notificationService.sendNotification(NotificationDescription.MaterialTypeDeleted, Map.of("name", materialType.getName()));
    }

    public void updateMaterialType(MaterialTypeDTO materialTypeDTO) {
        MaterialType materialType = materialTypeRepository.findById(materialTypeDTO.getId()).orElseThrow(() -> new RuntimeException("Material type not found"));
        materialType.setName(materialTypeDTO.getName());
        materialType.setDensity(materialTypeDTO.getDensity());
        materialTypeRepository.save(materialType);
        notificationService.sendNotification(NotificationDescription.MaterialTypeUpdated, Map.of("name", materialType.getName()));

    }
}
