package com.example.infraboxapi.materialGroup;

import com.example.infraboxapi.materialType.MaterialType;
import com.example.infraboxapi.materialType.MaterialTypeDTO;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@AllArgsConstructor
public class MaterialGroupService {

    private final MaterialGroupRepository materialGroupRepository;
    private final NotificationService notificationService;

    @Transactional
    public void createMaterialGroup(MaterialGroupDTO materialGroupDTO) {

        MaterialGroup materialGroup = MaterialGroup.builder()
                .name(materialGroupDTO.getName())
                .type(materialGroupDTO.getType())
                .imageURL(materialGroupDTO.getImageURL())
//                .materialType(convertToMaterialDescription(materialGroupDTO.getMaterialDescription()))
                .materials(new ArrayList<>())
                .build();

        materialGroupRepository.save(materialGroup);

        notificationService.createAndSendNotification("A new material group has been added: " + materialGroup.getName(), NotificationDescription.MaterialGroupAdded);

    }

    @Transactional
    public void updateMaterialGroup(MaterialGroupDTO materialGroupDTO) {

        MaterialGroup materialGroup = materialGroupRepository.findById(materialGroupDTO.getId()).orElseThrow(() -> new RuntimeException("Material Group not found"));
        materialGroup.setId(materialGroupDTO.getId());
        materialGroup.setImageURL(materialGroupDTO.getImageURL());
        materialGroup.setType(materialGroupDTO.getType());
        materialGroup.setName(materialGroupDTO.getName());

        materialGroupRepository.save(materialGroup);

        notificationService.createAndSendNotification(
                "The material group '" + materialGroup.getName() + "' has been updated successfully.",
                NotificationDescription.MaterialGroupUpdated
        );
    }

    @Transactional
    public void deleteMaterialGroup(Integer id) {

        MaterialGroup materialGroup = materialGroupRepository.findById(id).orElseThrow(() -> new RuntimeException("Material Group not found"));
        materialGroupRepository.delete(materialGroup);

        notificationService.createAndSendNotification("The material group '" + materialGroup.getName() + "' has been successfully deleted.", NotificationDescription.MaterialGroupDeleted);
    }


    public MaterialGroup getMaterialGroup(Integer id) {

        return materialGroupRepository.findById(id).orElseThrow(() -> new RuntimeException("Material Group not found"));
    }

    public Iterable<MaterialGroup> getMaterialGroups() {
        return materialGroupRepository.findAll();
    }


    public MaterialType convertToMaterialDescription(MaterialTypeDTO materialTypeDTO) {
        return MaterialType.builder()
                .id(materialTypeDTO.getId())
                .name(materialTypeDTO.getName())
                .density(materialTypeDTO.getDensity())
                .build();
    }
}
