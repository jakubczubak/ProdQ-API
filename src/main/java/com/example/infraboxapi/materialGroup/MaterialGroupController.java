package com.example.infraboxapi.materialGroup;


import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/material_group/")
@CrossOrigin(origins = "http://localhost:3000")
@AllArgsConstructor
public class MaterialGroupController {

    private final MaterialGroupService materialGroupService;


    @PostMapping("/create")
    public ResponseEntity<String> createMaterialGroup(@Valid @RequestBody MaterialGroupDTO materialGroupDTO, BindingResult  bindingResult){

        if (bindingResult.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid data: " + "Please check the provided information and try again.");
        }

        try{
            materialGroupService.createMaterialGroup(materialGroupDTO);
            return ResponseEntity.ok("Material Group created");
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating material group: " + e.getMessage());
        }

    }

    @PutMapping("/update")
    public ResponseEntity<String> updateMaterialGroup(@Valid @RequestBody MaterialGroupDTO materialGroupDTO, BindingResult  bindingResult){

        if (bindingResult.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid data: " + "Please check the provided information and try again.");
        }

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
