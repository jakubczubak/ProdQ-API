package com.example.infraboxapi.recycling;

import com.example.infraboxapi.common.CommonService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recycling/")
@AllArgsConstructor
public class RecyclingController {

    private final RecyclingService recyclingService;
    private final CommonService commonService;


    @GetMapping("/all")
    public ResponseEntity<List<Recycling>> getAllRecycling() {
        try{
            return ResponseEntity.ok(recyclingService.getAllRecycling());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/add")
    public ResponseEntity<String> addRecycling(@Valid @RequestBody RecyclingDTO recyclingDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }
        try{
            recyclingService.addRecycling(recyclingDTO);
            return ResponseEntity.ok("Recycling added");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteRecycling(@PathVariable Integer id) {
        try{
            recyclingService.deleteRecycling(id);
            return ResponseEntity.ok("Recycling deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping("/update")
    public ResponseEntity<String> updateRecycling(@Valid @RequestBody RecyclingDTO recyclingDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }
        try{
            recyclingService.updateRecycling(recyclingDTO);
            return ResponseEntity.ok("Recycling updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/quantity")
    public ResponseEntity<Double> getTotalRecyclingQuantity() {
        try{
            return ResponseEntity.ok(recyclingService.getTotalRecyclingQuantity());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    @GetMapping("/refund")
    public ResponseEntity<Double> getTotalRefund() {
        try{
            return ResponseEntity.ok(recyclingService.getTotalRefund());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
