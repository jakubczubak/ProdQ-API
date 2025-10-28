package com.example.infraboxapi.materialReservation;

public enum ReservationStatus {
    RESERVED,   // Material soft-locked, not yet consumed
    CONSUMED,   // Material consumed from inventory (irreversible)
    CANCELLED   // Reservation cancelled (e.g., program deleted)
}