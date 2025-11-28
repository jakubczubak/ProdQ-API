package com.example.prodqapi.material;

import com.example.prodqapi.materialGroup.MaterialGroup;
import com.example.prodqapi.materialGroup.MaterialGroupRepository;
import com.example.prodqapi.materialPriceHistory.MaterialPriceHistory;
import com.example.prodqapi.notification.NotificationDescription;
import com.example.prodqapi.notification.NotificationService;
import com.example.prodqapi.orderItem.OrderItem; // Dodany import
import com.example.prodqapi.orderItem.OrderItemRepository; // Dodany import
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@AllArgsConstructor
public class MaterialService {

    private final MaterialGroupRepository materialGroupRepository;
    private final MaterialRepository materialRepository;
    private final NotificationService notificationService;
    private final OrderItemRepository orderItemRepository; // Dodane pole

    @Transactional
    public void createMaterial(MaterialDTO materialDTO) {

        MaterialGroup materialGroup = materialGroupRepository.findById(materialDTO.getMaterialGroupID())
                .orElseThrow(() -> new RuntimeException("Material group not found"));


        Material newMaterial = Material.builder()
                .diameter(materialDTO.getDiameter())
                .length(materialDTO.getLength())
                .innerDiameter(materialDTO.getInnerDiameter())
                .name(materialDTO.getName())
                .price(materialDTO.getPrice())
                .pricePerKg(materialDTO.getPricePerKg())
                .minQuantity(materialDTO.getMinQuantity())
                .stockQuantity(materialDTO.getStockQuantity())
                .totalStockLength(materialDTO.getTotalStockLength())
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

        notificationService.sendNotification(NotificationDescription.MaterialAdded, Map.of("name", newMaterial.getName()));
    }

    @Transactional
    public void deleteMaterial(Integer id) {
        // Sprawdź, czy materiał istnieje, zanim zaczniesz działać
        Material materialToDelete = materialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Material not found with id: " + id));

        // 1. Znajdź wszystkie pozycje zamówień (OrderItem) powiązane z tym materiałem
        List<OrderItem> relatedOrderItems = orderItemRepository.findByMaterialId(id);

        // 2. W każdej powiązanej pozycji zamówienia ustaw pole 'material' na null, aby zerwać powiązanie
        for (OrderItem item : relatedOrderItems) {
            item.setMaterial(null);
        }
        orderItemRepository.saveAll(relatedOrderItems); // Zapisz zmiany w pozycjach zamówień

        // 3. Teraz, gdy powiązania są usunięte, możesz bezpiecznie usunąć materiał
        String materialName = materialToDelete.getName();
        materialRepository.deleteById(id);

        // 4. Wyślij powiadomienie
        notificationService.sendNotification(NotificationDescription.MaterialDeleted, Map.of("name", materialName));
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

        // Zmiana ilości - sprawdź stockQuantity lub totalStockLength w zależności od typu
        if (materialDTO.getStockQuantity() != null && !materialDTO.getStockQuantity().equals(material.getStockQuantity())) {
            int oldQty = material.getStockQuantity() != null ? material.getStockQuantity() : 0;
            notificationService.sendQuantityNotification(NotificationDescription.MaterialQuantityUpdated, Map.of(
                    "name", material.getName(),
                    "oldValue", String.valueOf(oldQty),
                    "newValue", String.valueOf(materialDTO.getStockQuantity()),
                    "unit", "szt",
                    "action", materialDTO.getStockQuantity() > oldQty ? "increased" : "decreased"
            ));
        }
        if (materialDTO.getTotalStockLength() != null && !materialDTO.getTotalStockLength().equals(material.getTotalStockLength())) {
            double oldLength = material.getTotalStockLength() != null ? material.getTotalStockLength() : 0;
            notificationService.sendQuantityNotification(NotificationDescription.MaterialQuantityUpdated, Map.of(
                    "name", material.getName(),
                    "oldValue", String.valueOf(oldLength),
                    "newValue", String.valueOf(materialDTO.getTotalStockLength()),
                    "unit", "mm",
                    "action", materialDTO.getTotalStockLength() > oldLength ? "increased" : "decreased"
            ));
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
        if (materialDTO.getInnerDiameter() != null && !materialDTO.getInnerDiameter().equals(material.getInnerDiameter())) {
            notificationMessage.append("\nŚrednica wewnętrzna: z ")
                    .append(material.getInnerDiameter() != null ? material.getInnerDiameter() : 0)
                    .append(" na ")
                    .append(materialDTO.getInnerDiameter());
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
        material.setStockQuantity(materialDTO.getStockQuantity());
        material.setTotalStockLength(materialDTO.getTotalStockLength());
        material.setZ(materialDTO.getZ());
        material.setY(materialDTO.getY());
        material.setX(materialDTO.getX());
        material.setDiameter(materialDTO.getDiameter());
        material.setLength(materialDTO.getLength());
        material.setInnerDiameter(materialDTO.getInnerDiameter());
        material.setName(materialDTO.getName());
        material.setType(materialDTO.getType());
        material.setQuantityInTransit(materialDTO.getQuantityInTransit());
        material.setAdditionalInfo(materialDTO.getAdditionalInfo());

        materialRepository.save(material);

        notificationService.sendNotification(NotificationDescription.MaterialUpdated, Map.of("name", material.getName()));
    }
}