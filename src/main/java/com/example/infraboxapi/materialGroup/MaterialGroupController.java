package com.example.infraboxapi.materialGroup;


import com.example.infraboxapi.FileImage.FileImageService;
import com.example.infraboxapi.common.CommonService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/material_group/")
@AllArgsConstructor
public class MaterialGroupController {

    private final MaterialGroupService materialGroupService;
    private final CommonService commonService;

    @PostMapping("/create")
    public ResponseEntity<String> createMaterialGroup(@ModelAttribute @Valid MaterialGroupDTO materialGroupDTO,
                                                      BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }

        try {
            materialGroupService.createMaterialGroup(materialGroupDTO);
            return ResponseEntity.ok("Material Group created");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating material group: " + e.getMessage());
        }

    }

    @PutMapping("/update")
    public ResponseEntity<String> updateMaterialGroup(@ModelAttribute @Valid MaterialGroupDTO materialGroupDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }

        try {
            materialGroupService.updateMaterialGroup(materialGroupDTO);
            return ResponseEntity.ok("Material Group updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating material group: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteMaterialGroup(@PathVariable Integer id) {

        try {
            materialGroupService.deleteMaterialGroup(id);
            return ResponseEntity.ok("Material Group deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting material group: " + e.getMessage());
        }
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<MaterialGroup> getMaterialGroup(@PathVariable Integer id) {


        try {
            return ResponseEntity.ok(materialGroupService.getMaterialGroup(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/get")
    public ResponseEntity<List<MaterialGroup>> getMaterialGroups() {
        try {
            List<MaterialGroup> groups = materialGroupService.getMaterialGroups();
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/delete/{fileID}/{materialGroupID}")
    public ResponseEntity<String> deleteFileImage(@PathVariable Integer fileID, @PathVariable Integer materialGroupID) {

        try {
            materialGroupService.deleteFile(fileID, materialGroupID);
            return ResponseEntity.ok("Material group image deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting material group image: " + e.getMessage());
        }
    }


}
