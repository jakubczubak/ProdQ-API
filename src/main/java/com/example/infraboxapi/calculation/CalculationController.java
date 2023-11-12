package com.example.infraboxapi.calculation;


import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calculation/")
@AllArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class CalculationController {

    private final CalculationService calculationService;


    @GetMapping("/all")
    public ResponseEntity<List<Calculation>> getAllCalculations() {
        return ResponseEntity.ok(calculationService.getAllCalculations());
    }

    @PostMapping("/add")
    public ResponseEntity<Calculation> addCalculation(@RequestBody CalculationDTO calculationDTO) {
        return ResponseEntity.ok(calculationService.addCalculation(calculationDTO));
    }


}
