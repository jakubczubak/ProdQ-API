package com.example.infraboxapi.materialReservation.dto;

import com.example.infraboxapi.materialReservation.MaterialProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialReservationDTO {

    private Integer productionQueueItemId;

    // Database material fields
    private Boolean isCustom;
    private Integer materialId;

    // Custom material fields
    private String customName;
    private MaterialProfile customType;
    private Double customX;
    private Double customY;
    private Double customZ;
    private Double customDiameter;
    private Double customInnerDiameter;
    private Integer customMaterialTypeId;

    // Common fields
    private Double quantityOrLength;
    private Double weight;
    private Double cost;
}