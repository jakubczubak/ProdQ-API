package com.example.prodqapi.supplier;


import com.example.prodqapi.common.CommonService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/supplier/")
@AllArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    private final CommonService commonService;
    private final SupplierPerformanceService supplierPerformanceService;

    @GetMapping("/all")
    public ResponseEntity<List<Supplier>> getAllSuppliers() {

        try {
            return ResponseEntity.ok(supplierService.getAllSuppliers());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteSupplier(@PathVariable Integer id) {

        try {
            supplierService.deleteSupplier(id);
            return ResponseEntity.ok("Supplier deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting supplier: " + e.getMessage());
        }
    }

    @PutMapping("/update")
    public ResponseEntity<String> updateSupplier(@Valid @RequestBody SupplierDTO supplierDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }
        try {
            supplierService.updateSupplier(supplierDTO);
            return ResponseEntity.ok("Supplier updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating supplier: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public ResponseEntity<String> createSupplier(@Valid @RequestBody SupplierDTO supplierDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }
        try {
            supplierService.createSupplier(supplierDTO);
            return ResponseEntity.ok("Supplier created");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating supplier: " + e.getMessage());
        }
    }

    // === Supplier Performance Endpoints ===

    /**
     * Get performance metrics for a specific supplier
     */
    @GetMapping("/{id}/performance")
    public ResponseEntity<SupplierPerformanceDTO> getSupplierPerformance(@PathVariable Integer id) {
        try {
            SupplierPerformanceDTO performance = supplierPerformanceService.getPerformance(id);
            if (performance == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(performance);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Get ranking of all suppliers by overall score
     */
    @GetMapping("/ranking")
    public ResponseEntity<List<SupplierPerformanceDTO>> getSupplierRanking() {
        try {
            List<SupplierPerformanceDTO> ranking = supplierPerformanceService.getSupplierRanking();
            return ResponseEntity.ok(ranking);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Manually trigger recalculation of all supplier performance metrics
     * Admin endpoint
     */
    @PostMapping("/performance/recalculate")
    public ResponseEntity<String> recalculateAllPerformance() {
        try {
            supplierPerformanceService.recalculateAllSuppliers();
            return ResponseEntity.ok("Supplier performance metrics recalculated successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error recalculating performance: " + e.getMessage());
        }
    }

    /**
     * Recalculate performance for a single supplier
     */
    @PostMapping("/{id}/performance/recalculate")
    public ResponseEntity<SupplierPerformanceDTO> recalculateSupplierPerformance(@PathVariable Integer id) {
        try {
            SupplierPerformanceDTO performance = supplierPerformanceService.recalculateSupplier(id);
            return ResponseEntity.ok(performance);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
