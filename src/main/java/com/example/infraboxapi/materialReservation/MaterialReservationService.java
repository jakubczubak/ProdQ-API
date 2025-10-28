package com.example.infraboxapi.materialReservation;

import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.material.MaterialRepository;
import com.example.infraboxapi.materialGroup.MaterialGroup;
import com.example.infraboxapi.materialGroup.MaterialGroupRepository;
import com.example.infraboxapi.materialReservation.dto.MaterialAvailabilityDTO;
import com.example.infraboxapi.materialReservation.dto.MaterialReservationDTO;
import com.example.infraboxapi.materialReservation.dto.MaterialWithAvailabilityDTO;
import com.example.infraboxapi.materialReservation.exception.InsufficientMaterialException;
import com.example.infraboxapi.materialType.MaterialType;
import com.example.infraboxapi.materialType.MaterialTypeRepository;
import com.example.infraboxapi.productionQueueItem.ProductionQueueItem;
import com.example.infraboxapi.productionQueueItem.ProductionQueueItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialReservationService {

    private final MaterialReservationRepository reservationRepository;
    private final MaterialRepository materialRepository;
    private final MaterialTypeRepository materialTypeRepository;
    private final ProductionQueueItemRepository productionQueueItemRepository;
    private final MaterialGroupRepository materialGroupRepository;

    @Transactional(readOnly = true)
    public MaterialReservation findByProductionQueueItemId(Integer productionQueueItemId) {
        if (productionQueueItemId == null) {
            return null;
        }
        return reservationRepository.findByProductionQueueItemId(productionQueueItemId)
            .orElse(null);
    }

    @Transactional
    public MaterialReservation createReservation(MaterialReservationDTO dto) {
        MaterialReservation reservation = new MaterialReservation();

        // Set production queue item (can be null for quick reservations)
        if (dto.getProductionQueueItemId() != null) {
            ProductionQueueItem program = productionQueueItemRepository.findById(dto.getProductionQueueItemId())
                .orElseThrow(() -> new IllegalArgumentException("Production queue item not found"));
            reservation.setProductionQueueItem(program);
        }

        reservation.setIsCustom(dto.getIsCustom());

        if (!dto.getIsCustom()) {
            // Database material
            Material material = materialRepository.findById(dto.getMaterialId())
                .orElseThrow(() -> new IllegalArgumentException("Material not found"));

            // Validate availability
            MaterialAvailabilityDTO availability = validateAvailability(
                dto.getMaterialId(),
                dto.getQuantityOrLength(),
                null
            );

            if (!availability.getAvailable()) {
                throw new InsufficientMaterialException(
                    String.format("Insufficient material available. Required: %.2f, Available: %.2f",
                        dto.getQuantityOrLength(), availability.getAvailableQuantity())
                );
            }

            reservation.setMaterial(material);
        } else {
            // Custom material
            reservation.setCustomName(dto.getCustomName());
            reservation.setCustomType(dto.getCustomType());
            reservation.setCustomX(dto.getCustomX());
            reservation.setCustomY(dto.getCustomY());
            reservation.setCustomZ(dto.getCustomZ());
            reservation.setCustomDiameter(dto.getCustomDiameter());
            reservation.setCustomInnerDiameter(dto.getCustomInnerDiameter());

            if (dto.getCustomMaterialTypeId() != null) {
                MaterialType materialType = materialTypeRepository.findById(dto.getCustomMaterialTypeId())
                    .orElseThrow(() -> new IllegalArgumentException("Material type not found"));
                reservation.setCustomMaterialType(materialType);
            }
        }

        reservation.setQuantityOrLength(dto.getQuantityOrLength());
        reservation.setWeight(dto.getWeight());
        reservation.setCost(dto.getCost());
        reservation.setStatus(ReservationStatus.RESERVED);

        return reservationRepository.save(reservation);
    }

    @Transactional
    public MaterialReservation updateReservation(Integer reservationId, MaterialReservationDTO dto) {
        MaterialReservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));

        // For database materials, validate availability
        if (!dto.getIsCustom()) {
            MaterialAvailabilityDTO availability = validateAvailability(
                dto.getMaterialId(),
                dto.getQuantityOrLength(),
                reservationId
            );

            if (!availability.getAvailable()) {
                throw new InsufficientMaterialException(
                    String.format("Insufficient material available. Required: %.2f, Available: %.2f",
                        dto.getQuantityOrLength(), availability.getAvailableQuantity())
                );
            }

            Material material = materialRepository.findById(dto.getMaterialId())
                .orElseThrow(() -> new IllegalArgumentException("Material not found"));
            reservation.setMaterial(material);
            reservation.setIsCustom(false);
        } else {
            reservation.setIsCustom(true);
            reservation.setMaterial(null);
            reservation.setCustomName(dto.getCustomName());
            reservation.setCustomType(dto.getCustomType());
            reservation.setCustomX(dto.getCustomX());
            reservation.setCustomY(dto.getCustomY());
            reservation.setCustomZ(dto.getCustomZ());
            reservation.setCustomDiameter(dto.getCustomDiameter());
            reservation.setCustomInnerDiameter(dto.getCustomInnerDiameter());

            if (dto.getCustomMaterialTypeId() != null) {
                MaterialType materialType = materialTypeRepository.findById(dto.getCustomMaterialTypeId())
                    .orElseThrow(() -> new IllegalArgumentException("Material type not found"));
                reservation.setCustomMaterialType(materialType);
            } else {
                reservation.setCustomMaterialType(null);
            }
        }

        reservation.setQuantityOrLength(dto.getQuantityOrLength());
        reservation.setWeight(dto.getWeight());
        reservation.setCost(dto.getCost());

        return reservationRepository.save(reservation);
    }

    @Transactional
    public void deleteReservation(Integer reservationId) {
        reservationRepository.deleteById(reservationId);
    }

    @Transactional(readOnly = true)
    public MaterialAvailabilityDTO validateAvailability(
        Integer materialId,
        Double requestedQuantity,
        Integer excludeReservationId
    ) {
        Material material = materialRepository.findById(materialId)
            .orElseThrow(() -> new IllegalArgumentException("Material not found"));

        Double reservedQuantity = reservationRepository.sumReservedQuantity(
            materialId,
            ReservationStatus.RESERVED,
            excludeReservationId
        );

        if (reservedQuantity == null) {
            reservedQuantity = 0.0;
        }

        // Calculate available quantity
        // For Plate: quantity is number of pieces
        // For Rod/Tube: available = length * quantity (quantity is coefficient 0-1)
        Double stockQuantity;
        String unit;

        if ("Plate".equals(material.getType())) {
            stockQuantity = (double) material.getQuantity();
            unit = "szt";
        } else {
            // Rod or Tube: available length = length * quantity
            stockQuantity = (double) (material.getLength() * material.getQuantity());
            unit = "mm";
        }

        Double availableQuantity = stockQuantity - reservedQuantity;
        Double shortage = requestedQuantity - availableQuantity;
        boolean available = availableQuantity >= requestedQuantity;

        String message;
        if (available) {
            message = "✅ Materiał dostępny";
        } else {
            message = String.format("⚠️ Niewystarczająca ilość! Brakuje: %.2f %s", shortage, unit);
        }

        return MaterialAvailabilityDTO.builder()
            .available(available)
            .availableQuantity(availableQuantity)
            .requestedQuantity(requestedQuantity)
            .shortage(shortage > 0 ? shortage : 0.0)
            .message(message)
            .build();
    }

    @Transactional(readOnly = true)
    public List<MaterialWithAvailabilityDTO> getMaterialsByGroupWithAvailability(Integer groupId) {
        MaterialGroup materialGroup = materialGroupRepository.findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("Material group not found"));

        List<Material> materials = materialGroup.getMaterials();

        return materials.stream().map(material -> {
            Double reservedQuantity = reservationRepository.sumReservedQuantity(
                material.getId(),
                ReservationStatus.RESERVED,
                null
            );

            if (reservedQuantity == null) {
                reservedQuantity = 0.0;
            }

            Double stockQuantity;
            if ("Plate".equals(material.getType())) {
                stockQuantity = (double) material.getQuantity();
            } else {
                stockQuantity = (double) (material.getLength() * material.getQuantity());
            }

            Double availableQuantity = stockQuantity - reservedQuantity;

            // Get existing reservations
            List<MaterialReservation> reservations = reservationRepository
                .findReservationsWithDetailsForMaterial(material.getId());

            List<MaterialWithAvailabilityDTO.ExistingReservationDTO> existingReservations =
                reservations.stream()
                    .filter(r -> r.getProductionQueueItem() != null) // Filter out quick reservations
                    .map(r ->
                        MaterialWithAvailabilityDTO.ExistingReservationDTO.builder()
                            .programId(r.getProductionQueueItem().getId())
                            .programName(r.getProductionQueueItem().getOrderName() + " - " +
                                        r.getProductionQueueItem().getPartName())
                            .quantityOrLength(r.getQuantityOrLength())
                            .status(r.getStatus().name())
                            .build()
                    ).collect(Collectors.toList());

            // Build MaterialTypeDTO
            MaterialWithAvailabilityDTO.MaterialTypeDTO materialTypeDTO = null;
            if (materialGroup.getMaterialType() != null) {
                materialTypeDTO = MaterialWithAvailabilityDTO.MaterialTypeDTO.builder()
                    .id(materialGroup.getMaterialType().getId())
                    .name(materialGroup.getMaterialType().getName())
                    .density(materialGroup.getMaterialType().getDensity())
                    .build();
            }

            return MaterialWithAvailabilityDTO.builder()
                .id(material.getId())
                .materialGroupId(groupId)
                .materialGroupName(materialGroup.getName())
                .materialType(materialTypeDTO)
                .profile(material.getType())
                .x((double) material.getX())
                .y((double) material.getY())
                .z((double) material.getZ())
                .diameter((double) material.getDiameter())
                .thickness((double) material.getThickness())
                .length((double) material.getLength())
                .quantity(material.getQuantity())
                .pricePerKg(material.getPricePerKg())
                .availableQuantity(availableQuantity)
                .reservedQuantity(reservedQuantity)
                .existingReservations(existingReservations)
                .build();
        }).collect(Collectors.toList());
    }
}