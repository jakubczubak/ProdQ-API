package com.example.infraboxapi.accessorieItem;


import com.example.infraboxapi.common.CommonService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accessorie/item/")
@AllArgsConstructor
public class AccessorieItemController {

    private final AccessorieItemService accessorieItemService;
    private final CommonService commonService;

    @PostMapping("/create")
    public ResponseEntity<String> createAccessorieItem(@Valid @RequestBody AccessorieItemDTO accessorieItemDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }

        try {
            accessorieItemService.createAccessorieItem(accessorieItemDTO);
            return ResponseEntity.ok("Accessorie item created");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating accessorie item: " + e.getMessage());
        }
    }

    @PutMapping("/update")
    public ResponseEntity<String> updateAccessorieItem(@Valid @RequestBody AccessorieItemDTO accessorieItemDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }

        try {
            accessorieItemService.updateAccessorieItem(accessorieItemDTO);
            return ResponseEntity.ok("Accessorie item updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating accessorie item: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteAccessorieItem(@PathVariable Integer id) {

        try {
            accessorieItemService.deleteAccessorieItem(id);
            return ResponseEntity.ok("Accessorie item deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting accessorie item: " + e.getMessage());
        }
    }
}
