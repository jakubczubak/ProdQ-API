package com.example.infraboxapi.material;

import com.example.infraboxapi.materialGroup.MaterialGroup;
import com.example.infraboxapi.materialGroup.MaterialGroupRepository;
import com.example.infraboxapi.materialPriceHistory.MaterialPriceHistory;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Objects;

@Service
@AllArgsConstructor
public class MaterialService {

    private final MaterialGroupRepository materialGroupRepository;
    private final MaterialRepository materialRepository;
    private final NotificationService notificationService;

    @Transactional
    public void createMaterial(MaterialDTO materialDTO) {

        MaterialGroup materialGroup = materialGroupRepository.findById(materialDTO.getMaterialGroupID())
                .orElseThrow(() -> new RuntimeException("Material group not found"));


        Material newMaterial = Material.builder()
                .diameter(materialDTO.getDiameter())
                .length(materialDTO.getLength())
                .thickness(materialDTO.getThickness())
                .name(materialDTO.getName())
                .price(materialDTO.getPrice())
                .pricePerKg(materialDTO.getPricePerKg())
                .minQuantity(materialDTO.getMinQuantity())
                .quantity(materialDTO.getQuantity())
                .z(materialDTO.getZ())
                .y(materialDTO.getY())
                .x(materialDTO.getX())
                .type(materialDTO.getType())
                .quantityInTransit(materialDTO.getQuantityInTransit())
                .additionalInfo(materialDTO.getAdditionalInfo())
                .build();

        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        MaterialPriceHistory materialPriceHistory = MaterialPriceHistory.builder()
                .price(materialDTO.getPricePerKg())
                .date(currentDateTime.format(formatter)).

                build();

        newMaterial.setMaterialPriceHistoryList(new ArrayList<>());
        newMaterial.getMaterialPriceHistoryList().add(materialPriceHistory);

        materialGroup.getMaterials().add(newMaterial);

        materialGroupRepository.save(materialGroup);

        notificationService.createAndSendNotification("A new material '" + newMaterial.getName() + "` has been added successfully.", NotificationDescription.MaterialAdded);
    }

    @Transactional
    public void deleteMaterial(Integer id) {
        String materialName = materialRepository.findById(id).orElseThrow(() -> new RuntimeException("Material not found")).getName();
        materialRepository.deleteById(id);
        notificationService.createAndSendNotification("The material '" + materialName + "' has been successfully deleted.", NotificationDescription.MaterialDeleted);
    }
    @Transactional
    public void updateMaterial(MaterialDTO materialDTO) {
        Material material = materialRepository.findById(materialDTO.getId())
                .orElseThrow(() -> new RuntimeException("Materiał nie znaleziony"));

        StringBuilder notificationMessage = new StringBuilder("Materiał ")
                .append(material.getName())
                .append(" został zaktualizowany. Zmiany:");

        // Sprawdzenie zmiany ceny na kg
        if (material.getPricePerKg().compareTo(materialDTO.getPricePerKg()) != 0) {
            notificationMessage.append("\nCena za kg: z ")
                    .append(material.getPricePerKg())
                    .append(" na ")
                    .append(materialDTO.getPricePerKg());

            LocalDateTime currentDateTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            MaterialPriceHistory materialPriceHistory = MaterialPriceHistory.builder()
                    .price(materialDTO.getPricePerKg())
                    .date(currentDateTime.format(formatter))
                    .build();

            material.getMaterialPriceHistoryList().add(materialPriceHistory);
        }

        // Zmiana ilości
        if (material.getQuantity() != materialDTO.getQuantity()) {
            String message;
            if (materialDTO.getQuantity() > material.getQuantity()) {
                message = "Ilość materiału " + material.getName() + " została zwiększona z "
                        + material.getQuantity() + " na " + materialDTO.getQuantity() + ".";
            } else {
                message = "Ilość materiału " + material.getName() + " została zmniejszona z "
                        + material.getQuantity() + " na " + materialDTO.getQuantity() + ".";
            }
            notificationService.createAndSendQuantityNotification(message, NotificationDescription.MaterialQuantityUpdated);
        }

        // Pozostałe zmiany
        if (material.getMinQuantity() != materialDTO.getMinQuantity()) {
            notificationMessage.append("\nMinimalna ilość: z ")
                    .append(material.getMinQuantity())
                    .append(" na ")
                    .append(materialDTO.getMinQuantity());
        }
        if (material.getZ() != materialDTO.getZ()) {
            notificationMessage.append("\nGrubość (Z): z ")
                    .append(material.getZ())
                    .append(" na ")
                    .append(materialDTO.getZ());
        }
        if (material.getY() != materialDTO.getY()) {
            notificationMessage.append("\nWysokość (Y): z ")
                    .append(material.getY())
                    .append(" na ")
                    .append(materialDTO.getY());
        }
        if (material.getX() != materialDTO.getX()) {
            notificationMessage.append("\nSzerokość (X): z ")
                    .append(material.getX())
                    .append(" na ")
                    .append(materialDTO.getX());
        }
        if (material.getDiameter() != materialDTO.getDiameter()) {
            notificationMessage.append("\nŚrednica: z ")
                    .append(material.getDiameter())
                    .append(" na ")
                    .append(materialDTO.getDiameter());
        }
        if (material.getLength() != materialDTO.getLength()) {
            notificationMessage.append("\nDługość: z ")
                    .append(material.getLength())
                    .append(" na ")
                    .append(materialDTO.getLength());
        }
        if (material.getThickness() != materialDTO.getThickness()) {
            notificationMessage.append("\nGrubość: z ")
                    .append(material.getThickness())
                    .append(" na ")
                    .append(materialDTO.getThickness());
        }
        if (!Objects.equals(material.getAdditionalInfo(), materialDTO.getAdditionalInfo())) {
            notificationMessage.append("\nDodatkowe informacje: z ")
                    .append(material.getAdditionalInfo())
                    .append(" na ")
                    .append(materialDTO.getAdditionalInfo());
        }

        // Aktualizacja materiału
        material.setPricePerKg(materialDTO.getPricePerKg());
        material.setPrice(materialDTO.getPrice());
        material.setMinQuantity(materialDTO.getMinQuantity());
        material.setQuantity(materialDTO.getQuantity());
        material.setZ(materialDTO.getZ());
        material.setY(materialDTO.getY());
        material.setX(materialDTO.getX());
        material.setDiameter(materialDTO.getDiameter());
        material.setLength(materialDTO.getLength());
        material.setThickness(materialDTO.getThickness());
        material.setName(materialDTO.getName());
        material.setType(materialDTO.getType());
        material.setQuantityInTransit(materialDTO.getQuantityInTransit());
        material.setAdditionalInfo(materialDTO.getAdditionalInfo());

        materialRepository.save(material);

        notificationService.createAndSendNotification(notificationMessage.toString(), NotificationDescription.MaterialUpdated);
    }

}
