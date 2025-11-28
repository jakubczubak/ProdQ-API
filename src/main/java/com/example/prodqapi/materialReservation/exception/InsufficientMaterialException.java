package com.example.prodqapi.materialReservation.exception;

import lombok.Getter;

@Getter
public class InsufficientMaterialException extends RuntimeException {
    private final Integer materialId;
    private final String materialName;
    private final Double required;
    private final Double available;
    private final String unit;

    public InsufficientMaterialException(String message) {
        super(message);
        this.materialId = null;
        this.materialName = null;
        this.required = null;
        this.available = null;
        this.unit = null;
    }

    public InsufficientMaterialException(
        String message,
        Integer materialId,
        String materialName,
        Double required,
        Double available,
        String unit
    ) {
        super(message);
        this.materialId = materialId;
        this.materialName = materialName;
        this.required = required;
        this.available = available;
        this.unit = unit;
    }
}