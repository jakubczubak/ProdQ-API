package com.example.infraboxapi.materialGroup;

import com.example.infraboxapi.FileImage.FileImage;
import com.example.infraboxapi.FileImage.FileImageRepository;
import com.example.infraboxapi.FileImage.FileImageService;
import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.materialReservation.MaterialReservationRepository;
import com.example.infraboxapi.materialReservation.ReservationStatus;
import com.example.infraboxapi.materialType.MaterialType;
import com.example.infraboxapi.materialType.MaterialTypeRepository;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@AllArgsConstructor
public class MaterialGroupService {

    private final MaterialGroupRepository materialGroupRepository;
    private final NotificationService notificationService;
    private final MaterialTypeRepository materialTypeRepository;
    private final FileImageService fileImageService;
    private final FileImageRepository fileImageRepository;
    private final MaterialReservationRepository reservationRepository;

    @Transactional
    public void createMaterialGroup(MaterialGroupDTO materialGroupDTO) throws IOException {

        MaterialType materialType = materialTypeRepository.findById(materialGroupDTO.getMaterialTypeID()).orElseThrow(() -> new RuntimeException("Material Type not found"));


        MaterialGroup materialGroup = MaterialGroup.builder()
                .name(materialGroupDTO.getName())
                .type(materialGroupDTO.getType())
                .materialType(materialType)
                .materials(new ArrayList<>())
                .build();

        if (materialGroupDTO.getFile() != null) {

            FileImage fileImage = fileImageService.createFile(materialGroupDTO.getFile());
            materialGroup.setFileImage(fileImage);
        }

        materialGroupRepository.save(materialGroup);

        notificationService.createAndSendNotification("A new material group has been added: `" + materialGroup.getName() + "`", NotificationDescription.MaterialGroupAdded);

    }

    @Transactional
    public void updateMaterialGroup(MaterialGroupDTO materialGroupDTO) throws IOException {

        MaterialGroup materialGroup = materialGroupRepository.findById(materialGroupDTO.getId()).orElseThrow(() -> new RuntimeException("Material Group not found"));
        materialGroup.setName(materialGroupDTO.getName());


        if (materialGroupDTO.getFile() != null) {
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


    @Transactional
    public MaterialGroup getMaterialGroup(Integer id) {
        MaterialGroup materialGroup = materialGroupRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Material Group not found"));

        // Enrich with availability data
        enrichMaterialsWithAvailability(materialGroup);

        return materialGroup;
    }

    @Transactional
    public List<MaterialGroup> getMaterialGroups() {
        Iterable<MaterialGroup> materialGroupsIterable = materialGroupRepository.findAll();

        // Convert to list and enrich with availability data
        List<MaterialGroup> materialGroups = StreamSupport.stream(materialGroupsIterable.spliterator(), false)
            .collect(Collectors.toList());

        // Enrich each material with reservation data
        for (MaterialGroup materialGroup : materialGroups) {
            enrichMaterialsWithAvailability(materialGroup);
        }

        return materialGroups;
    }

    private void enrichMaterialsWithAvailability(MaterialGroup materialGroup) {
        if (materialGroup.getMaterials() == null) {
            return;
        }

        System.out.println("=== Enriching MaterialGroup: " + materialGroup.getName() + " ===");

        for (Material material : materialGroup.getMaterials()) {
            // Calculate reserved quantity
            Double reservedQuantity = reservationRepository.sumReservedQuantity(
                material.getId(),
                ReservationStatus.RESERVED,
                null
            );

            if (reservedQuantity == null) {
                reservedQuantity = 0.0;
            }

            // Calculate stock quantity based on material type
            Double stockQuantity;
            if ("Plate".equalsIgnoreCase(materialGroup.getType())) {
                // For plates: quantity is number of pieces
                stockQuantity = (double) material.getQuantity();
            } else {
                // For Rod/Tube: available length = length * quantity
                stockQuantity = (double) (material.getLength() * material.getQuantity());
            }

            // Calculate available quantity
            Double availableQuantity = stockQuantity - reservedQuantity;

            // Set transient fields
            material.setReservedQuantity(reservedQuantity);
            material.setAvailableQuantity(availableQuantity);

            System.out.println("Material ID: " + material.getId() +
                             " | Reserved: " + reservedQuantity +
                             " | Available: " + availableQuantity +
                             " | Stock: " + stockQuantity);

            // Verify getters are working
            System.out.println("Getter check - Reserved: " + material.getReservedQuantity() +
                             " | Available: " + material.getAvailableQuantity());
        }
    }


}
