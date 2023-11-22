package com.example.infraboxapi.supplier;


import com.example.infraboxapi.common.CommonService;
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
@CrossOrigin(origins = "http://localhost:3000")
public class SupplierController {

    private final SupplierService supplierService;
    private final CommonService commonService;
    @GetMapping("/all")
    public ResponseEntity<List<Supplier>> getAllSuppliers() {

        try{
            return ResponseEntity.ok(supplierService.getAllSuppliers());
        }catch (Exception e){
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

        commonService.handleBindingResult(bindingResult);
        try {
            supplierService.updateSupplier(supplierDTO);
            return ResponseEntity.ok("Supplier updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating supplier: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public ResponseEntity<String> createSupplier(@Valid @RequestBody SupplierDTO supplierDTO, BindingResult bindingResult) {

        commonService.handleBindingResult(bindingResult);
        try {
            supplierService.createSupplier(supplierDTO);
            return ResponseEntity.ok("Supplier created");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating supplier: " + e.getMessage());
        }
    }
}
