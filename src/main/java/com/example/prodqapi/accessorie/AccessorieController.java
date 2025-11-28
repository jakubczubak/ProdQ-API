package com.example.prodqapi.accessorie;


import com.example.prodqapi.common.CommonService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accessorie/")
@AllArgsConstructor
public class AccessorieController {


    private final CommonService commonService;
    private final AccessorieService accessorieService;
    @PostMapping("/create")
    public ResponseEntity<String> createAccessorie(@ModelAttribute @Valid AccessorieDTO accessorieDTO, BindingResult bindingResult) {
        if(bindingResult.hasErrors()){
            return commonService.handleBindingResult(bindingResult);
        }

        try {
            accessorieService.createAccessorie(accessorieDTO);
            return ResponseEntity.ok("Accessorie created");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating accessorie: " + e.getMessage());
        }
    }


    @PutMapping("/update")
    public ResponseEntity<String> updateAccessorie(@ModelAttribute @Valid AccessorieDTO accessorieDTO, BindingResult bindingResult) {

        if(bindingResult.hasErrors()){
            return commonService.handleBindingResult(bindingResult);
        }

        try {
            accessorieService.updateAccessorie(accessorieDTO);
            return ResponseEntity.ok("Accessorie updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating accessorie: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteAccessorie(@PathVariable Integer id) {
        try {
            accessorieService.deleteAccessorie(id);
            return ResponseEntity.ok("Accessorie deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting accessorie: " + e.getMessage());
        }
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<Accessorie> getAccessorie(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(accessorieService.getAccessorie(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/get")
    public ResponseEntity<Iterable<Accessorie>> getAccessories() {
        try {
            return ResponseEntity.ok(accessorieService.getAccessories());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/delete/{fileID}/{accessorieID}")
    public ResponseEntity<String> deleteAccessorieFile(@PathVariable Integer fileID, @PathVariable Integer accessorieID) {
        try {
            accessorieService.deleteAccessorieFile(fileID, accessorieID);
            return ResponseEntity.ok("Accessorie file deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting accessorie file: " + e.getMessage());
        }
    }
}
