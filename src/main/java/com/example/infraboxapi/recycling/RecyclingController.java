package com.example.infraboxapi.recycling;

import com.example.infraboxapi.common.CommonService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recycling/")
@AllArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
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
    public ResponseEntity<String> addRecycling(@RequestBody RecyclingDTO recyclingDTO) {
        try{
            recyclingService.addRecycling(recyclingDTO);
            return ResponseEntity.ok("Recycling added");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteRecycling(@PathVariable Integer id) {
        try{
            recyclingService.deleteRecycling(id);
            return ResponseEntity.ok("Recycling deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/update")
    public ResponseEntity<String> updateRecycling(@RequestBody Recycling recycling) {
        try{
            recyclingService.updateRecycling(recycling);
            return ResponseEntity.ok("Recycling updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
