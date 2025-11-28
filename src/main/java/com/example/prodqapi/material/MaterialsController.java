package com.example.prodqapi.material;

import com.example.prodqapi.materialReservation.MaterialReservationService;
import com.example.prodqapi.materialReservation.dto.MaterialWithAvailabilityDTO;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Separate controller for /api/materials/* endpoints to maintain frontend compatibility.
 * Frontend uses /api/materials/by-group/ (plural) for availability queries.
 */
@RestController
@RequestMapping("/api/materials")
@AllArgsConstructor
public class MaterialsController {

    private final MaterialReservationService materialReservationService;

    @GetMapping("/by-group/{groupId}")
    public ResponseEntity<List<MaterialWithAvailabilityDTO>> getMaterialsByGroup(
        @PathVariable Integer groupId,
        @RequestParam(required = false, defaultValue = "false") boolean includeAvailability
    ) {
        if (includeAvailability) {
            List<MaterialWithAvailabilityDTO> materials =
                materialReservationService.getMaterialsByGroupWithAvailability(groupId);
            return ResponseEntity.ok(materials);
        } else {
            // Return basic materials without availability - would need to implement in MaterialService
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(null);
        }
    }
}