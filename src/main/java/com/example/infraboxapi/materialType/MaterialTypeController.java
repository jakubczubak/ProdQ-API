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
}
