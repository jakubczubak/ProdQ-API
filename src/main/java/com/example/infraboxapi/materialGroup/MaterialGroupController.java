package com.example.infraboxapi.materialGroup;


import com.example.infraboxapi.materialDescription.MaterialDescriptionDTO;
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

    @PutMapping("/update")
    public ResponseEntity<String> updateMaterialGroup(@RequestBody MaterialGroupDTO materialGroupDTO){

        try{
            materialGroupService.updateMaterialGroup(materialGroupDTO);
            return ResponseEntity.ok("Material Group updated");
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating material group: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteMaterialGroup(@PathVariable Integer id){

        try{
            materialGroupService.deleteMaterialGroup(id);
            return ResponseEntity.ok("Material Group deleted");
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting material group: " + e.getMessage());
        }
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<MaterialGroup> getMaterialGroup(@PathVariable Integer id){


        try{
            return ResponseEntity.ok(materialGroupService.getMaterialGroup(id));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/get")
    public ResponseEntity<Iterable<MaterialGroup>> getMaterialGroups(){

        try{
            return ResponseEntity.ok(materialGroupService.getMaterialGroups());
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
