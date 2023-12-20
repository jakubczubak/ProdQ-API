package com.example.infraboxapi.materialGroup;

import com.example.infraboxapi.File.File;
import com.example.infraboxapi.File.FileService;
import com.example.infraboxapi.materialType.MaterialType;
import com.example.infraboxapi.materialType.MaterialTypeDTO;
import com.example.infraboxapi.materialType.MaterialTypeRepository;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;

@Service
@AllArgsConstructor
public class MaterialGroupService {

    private final MaterialGroupRepository materialGroupRepository;
    private final NotificationService notificationService;
    private final MaterialTypeRepository materialTypeRepository;
    private final FileService fileService;

    @Transactional
    public void createMaterialGroup(MaterialGroupDTO materialGroupDTO) throws IOException {

        MaterialType materialType = materialTypeRepository.findById(materialGroupDTO.getMaterialTypeID()).orElseThrow(() -> new RuntimeException("Material Type not found"));

        File file = fileService.createFile(materialGroupDTO.getFile());

        MaterialGroup materialGroup = MaterialGroup.builder()
                .name(materialGroupDTO.getName())
                .type(materialGroupDTO.getType())
                .file(file)
                .materialType(materialType)
                .materials(new ArrayList<>())
                .build();

        materialGroupRepository.save(materialGroup);

        notificationService.createAndSendNotification("A new material group has been added: " + materialGroup.getName(), NotificationDescription.MaterialGroupAdded);

    }

    @Transactional
    public void updateMaterialGroup(MaterialGroupDTO materialGroupDTO) throws IOException {

        MaterialType materialType = materialTypeRepository.findById(materialGroupDTO.getMaterialTypeID()).orElseThrow(() -> new RuntimeException("Material Type not found"));




        MaterialGroup materialGroup = materialGroupRepository.findById(materialGroupDTO.getId()).orElseThrow(() -> new RuntimeException("Material Group not found"));
        materialGroup.setName(materialGroupDTO.getName());
        materialGroup.setType(materialGroupDTO.getType());
        materialGroup.setMaterialType(materialType);


        if(materialGroupDTO.getFile() != null) {
            File file = fileService.createFile(materialGroupDTO.getFile());
            materialGroup.setFile(file);
        }


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



}
