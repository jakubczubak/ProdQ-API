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

        notificationService.createAndSendNotification("Material type " + materialType.getName() + " created", NotificationDescription.MaterialTypeAdded );
    }

    public List<MaterialType> getAllMaterials() {
        return materialTypeRepository.findAll();
    }

    public void deleteMaterialType(Integer id) {
        materialTypeRepository.deleteById(id);
        notificationService.createAndSendNotification("Material type deleted", NotificationDescription.MaterialTypeDeleted );
    }
}
