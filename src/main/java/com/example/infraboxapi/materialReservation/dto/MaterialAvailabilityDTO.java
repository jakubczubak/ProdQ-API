package com.example.infraboxapi.materialReservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialAvailabilityDTO {
    private Boolean available;
    private Double availableQuantity;
    private Double requestedQuantity;
    private Double shortage;
    private String message;
}