package com.example.infraboxapi.materialReservation.exception;

public class InsufficientMaterialException extends RuntimeException {
    public InsufficientMaterialException(String message) {
        super(message);
    }
}