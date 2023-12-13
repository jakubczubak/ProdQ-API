package com.example.infraboxapi.materialType;
import com.example.infraboxapi.common.CommonService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/material_type/")
@AllArgsConstructor
public class MaterialTypeController {

    private final MaterialTypeService materialTypeService;
    private final CommonService commonService;

    @PostMapping("/create")
    public ResponseEntity<String> createMaterial(@Valid @RequestBody MaterialTypeDTO materialTypeDTO, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }

        try {
            materialTypeService.createMaterialType(materialTypeDTO);
            return ResponseEntity.ok("Material type created");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating material type: " + e.getMessage());
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllMaterialsType() {
        try {
            return ResponseEntity.ok(materialTypeService.getAllMaterials());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error getting material types: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteMaterialType(@PathVariable Integer id) {
        try {
            materialTypeService.deleteMaterialType(id);
            return ResponseEntity.ok("Material type deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting material type: " + e.getMessage());
        }
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateMaterialType(@Valid @RequestBody MaterialTypeDTO materialTypeDTO, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }

        try {
            materialTypeService.updateMaterialType(materialTypeDTO);
            return ResponseEntity.ok("Material type updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating material type: " + e.getMessage());
        }
    }
}
