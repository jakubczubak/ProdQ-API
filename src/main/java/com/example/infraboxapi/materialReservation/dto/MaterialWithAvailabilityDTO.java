package com.example.infraboxapi.materialReservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialWithAvailabilityDTO {
    private Integer id;
    private Integer materialGroupId;
    private String materialGroupName;
    private String materialGroupType;
    private MaterialTypeDTO materialType;
    private String profile;
    private Double x;
    private Double y;
    private Double z;
    private Double diameter;
    private Double innerDiameter;
    private Double length;
    private Integer stockQuantity;
    private Float totalStockLength;
    private BigDecimal pricePerKg;
    private Double availableQuantity;
    private Double reservedQuantity;
    private List<ExistingReservationDTO> existingReservations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaterialTypeDTO {
        private Integer id;
        private String name;
        private Float density;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExistingReservationDTO {
        private Integer programId;
        private String programName;
        private Integer reservedQuantity;  // For Plates
        private Double reservedLength;     // For Rods/Tubes
        private String status;
    }
}