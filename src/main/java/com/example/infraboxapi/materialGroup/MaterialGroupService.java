package com.example.infraboxapi.materialGroup;

import com.example.infraboxapi.FileImage.FileImage;
import com.example.infraboxapi.FileImage.FileImageRepository;
import com.example.infraboxapi.FileImage.FileImageService;
import com.example.infraboxapi.materialType.MaterialType;
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
    private final FileImageService fileImageService;
    private final FileImageRepository fileImageRepository;

    @Transactional
    public void createMaterialGroup(MaterialGroupDTO materialGroupDTO) throws IOException {

        MaterialType materialType = materialTypeRepository.findById(materialGroupDTO.getMaterialTypeID()).orElseThrow(() -> new RuntimeException("Material Type not found"));



        MaterialGroup materialGroup = MaterialGroup.builder()
                .name(materialGroupDTO.getName())
                .type(materialGroupDTO.getType())
                .materialType(materialType)
                .materials(new ArrayList<>())
                .build();

        if(materialGroupDTO.getFile() != null) {

            FileImage fileImage = fileImageService.createFile(materialGroupDTO.getFile());
            materialGroup.setFileImage(fileImage);
        }

        materialGroupRepository.save(materialGroup);

        notificationService.createAndSendNotification("A new material group has been added: " + materialGroup.getName(), NotificationDescription.MaterialGroupAdded);

    }

    @Transactional
    public void updateMaterialGroup(MaterialGroupDTO materialGroupDTO) throws IOException {

        MaterialGroup materialGroup = materialGroupRepository.findById(materialGroupDTO.getId()).orElseThrow(() -> new RuntimeException("Material Group not found"));
        materialGroup.setName(materialGroupDTO.getName());


        if(materialGroupDTO.getFile() != null) {
            FileImage fileImage = fileImageService.updateFile(materialGroupDTO.getFile(), materialGroup.getFileImage());
            materialGroup.setFileImage(fileImage);
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


    public void deleteFile(Integer id, Integer materialGroupID) {

        MaterialGroup materialGroup = materialGroupRepository.findById(materialGroupID).orElseThrow(() -> new RuntimeException("Material Group not found"));

        FileImage fileImage = fileImageRepository.findById(Long.valueOf(id)).orElseThrow(() -> new RuntimeException("File not found"));

        materialGroup.setFileImage(null);

        materialGroupRepository.save(materialGroup);

        fileImageRepository.delete(fileImage);
    }


    public MaterialGroup getMaterialGroup(Integer id) {

        return materialGroupRepository.findById(id).orElseThrow(() -> new RuntimeException("Material Group not found"));
    }

    public Iterable<MaterialGroup> getMaterialGroups() {
        return materialGroupRepository.findAll();
    }



}
