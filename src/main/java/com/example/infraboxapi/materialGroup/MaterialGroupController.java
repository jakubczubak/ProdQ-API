package com.example.infraboxapi.materialGroup;


import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/material_group/")
@CrossOrigin(origins = "http://localhost:3000")
@AllArgsConstructor
public class MaterialGroupController {

    private final MaterialGroupService materialGroupService;


    @PostMapping("/create")
    public ResponseEntity<String> createMaterialGroup(@RequestBody MaterialGroupDTO materialGroupDTO){

        try{
            materialGroupService.createMaterialGroup(materialGroupDTO);
            return ResponseEntity.ok("Material Group created");
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating material group: " + e.getMessage());
        }
    }
}
