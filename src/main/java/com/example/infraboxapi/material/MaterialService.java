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
                .orElseThrow(() -> new RuntimeException("Material not found"));

        StringBuilder notificationMessage = new StringBuilder("The material ")
                .append(material.getName())
                .append(" has been updated. Changes:");

        // Sprawdzenie zmiany ceny na kg
        if (material.getPricePerKg().compareTo(materialDTO.getPricePerKg()) != 0) {
            notificationMessage.append("\nPrice per kg: from ")
                    .append(material.getPricePerKg())
                    .append(" to ")
                    .append(materialDTO.getPricePerKg());

            LocalDateTime currentDateTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            MaterialPriceHistory materialPriceHistory = MaterialPriceHistory.builder()
                    .price(materialDTO.getPricePerKg())
                    .date(currentDateTime.format(formatter))
                    .build();

            material.getMaterialPriceHistoryList().add(materialPriceHistory);
        }

        // Zmiana quantity
        if (material.getQuantity() != materialDTO.getQuantity()) {
            // Tworzymy jedno powiadomienie dla zmiany quantity
            String message;
            if (materialDTO.getQuantity() > material.getQuantity()) {
                message = "The quantity of material " + material.getName() + " has been increased from "
                        + material.getQuantity() + " to " + materialDTO.getQuantity() + ".";
            } else {
                message = "The quantity of material " + material.getName() + " has been decreased from "
                        + material.getQuantity() + " to " + materialDTO.getQuantity() + ".";
            }
            notificationService.createAndSendQuantityNotification(message, NotificationDescription.MaterialQuantityUpdated);
        }

        // Pozostałe zmiany
        if (material.getMinQuantity() != materialDTO.getMinQuantity()) {
            notificationMessage.append("\nMin Quantity: from ")
                    .append(material.getMinQuantity())
                    .append(" to ")
                    .append(materialDTO.getMinQuantity());
        }
        if (material.getZ() != materialDTO.getZ()) {
            notificationMessage.append("\nThickness (Z): from ")
                    .append(material.getZ())
                    .append(" to ")
                    .append(materialDTO.getZ());
        }
        if (material.getY() != materialDTO.getY()) {
            notificationMessage.append("\nHeight (Y): from ")
                    .append(material.getY())
                    .append(" to ")
                    .append(materialDTO.getY());
        }
        if (material.getX() != materialDTO.getX()) {
            notificationMessage.append("\nWidth (X): from ")
                    .append(material.getX())
                    .append(" to ")
                    .append(materialDTO.getX());
        }
        if (material.getDiameter() != materialDTO.getDiameter()) {
            notificationMessage.append("\nDiameter: from ")
                    .append(material.getDiameter())
                    .append(" to ")
                    .append(materialDTO.getDiameter());
        }
        if (material.getLength() != materialDTO.getLength()) {
            notificationMessage.append("\nLength: from ")
                    .append(material.getLength())
                    .append(" to ")
                    .append(materialDTO.getLength());
        }
        if (material.getThickness() != materialDTO.getThickness()) {
            notificationMessage.append("\nThickness: from ")
                    .append(material.getThickness())
                    .append(" to ")
                    .append(materialDTO.getThickness());
        }
        if (!material.getAdditionalInfo().equals(materialDTO.getAdditionalInfo())) {
            notificationMessage.append("\nAdditional info: from ")
                    .append(material.getAdditionalInfo())
                    .append(" to ")
                    .append(materialDTO.getAdditionalInfo());
        }

        // Teraz aktualizujemy materiał z nowymi wartościami
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

        // Zapisujemy zaktualizowany materiał
        materialRepository.save(material);

        // Wysyłamy powiadomienie o zaktualizowanym materiale
        notificationService.createAndSendNotification(notificationMessage.toString(), NotificationDescription.MaterialUpdated);
    }

}
