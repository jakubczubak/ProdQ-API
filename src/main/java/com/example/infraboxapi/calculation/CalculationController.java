package com.example.infraboxapi.calculation;


import com.example.infraboxapi.notification.NotificationService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calculation/")
@AllArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class CalculationController {

    private final CalculationService calculationService;
    ;


    @GetMapping("/all")
    public ResponseEntity<List<Calculation>> getAllCalculations() {
        try {
            return ResponseEntity.ok(calculationService.getAllCalculations());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/add")
    public ResponseEntity<String> addCalculation(@Valid @RequestBody CalculationDTO calculationDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid data: " + "Please check the provided information and try again.");
        }
        try {
            calculationService.addCalculation(calculationDTO);
            return ResponseEntity.ok("Calculation added");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/update")
    public ResponseEntity<String> updateCalculation(@Valid @RequestBody CalculationDTO calculationDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid data: " + "Please check the provided information and try again.");
        }
        try {
            calculationService.updateCalculation(calculationDTO);
            return ResponseEntity.ok("Calculation updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteCalculation(@PathVariable Integer id) {
        try {
            calculationService.deleteCalculation(id);
            return ResponseEntity.ok("Calculation deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


}

