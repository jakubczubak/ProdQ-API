package com.example.infraboxapi.materialReservation;

import com.example.infraboxapi.materialReservation.dto.MaterialAvailabilityDTO;
import com.example.infraboxapi.materialReservation.dto.MaterialReservationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/material-reservations")
@RequiredArgsConstructor
public class MaterialReservationController {

    private final MaterialReservationService reservationService;

    @PostMapping
    public ResponseEntity<MaterialReservation> createReservation(@RequestBody MaterialReservationDTO dto) {
        MaterialReservation reservation = reservationService.createReservation(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(reservation);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MaterialReservation> updateReservation(
        @PathVariable Integer id,
        @RequestBody MaterialReservationDTO dto
    ) {
        MaterialReservation reservation = reservationService.updateReservation(id, dto);
        return ResponseEntity.ok(reservation);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReservation(@PathVariable Integer id) {
        reservationService.deleteReservation(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-program/{programId}")
    public ResponseEntity<MaterialReservation> getReservationByProgram(@PathVariable Integer programId) {
        MaterialReservation reservation = reservationService.findByProductionQueueItemId(programId);
        if (reservation == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(reservation);
    }

    @PostMapping("/validate-availability")
    public ResponseEntity<MaterialAvailabilityDTO> validateAvailability(
        @RequestBody Map<String, Object> request
    ) {
        Integer materialId = Integer.valueOf(request.get("materialId").toString());
        Double requestedQuantity = Double.valueOf(request.get("requestedQuantity").toString());
        Integer excludeReservationId = request.get("excludeReservationId") != null
            ? Integer.valueOf(request.get("excludeReservationId").toString())
            : null;

        MaterialAvailabilityDTO availability = reservationService.validateAvailability(
            materialId,
            requestedQuantity,
            excludeReservationId
        );

        return ResponseEntity.ok(availability);
    }
}