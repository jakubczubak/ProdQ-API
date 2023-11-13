package com.example.infraboxapi.departmentCost;


import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/department_cost/")
@AllArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class DepartmentCostController {

    private final DepartmentCostService departmentCostService;


    @GetMapping("/get")
    public ResponseEntity<DepartmentCost> getDepartmentCost() {

        try {
            return ResponseEntity.ok(departmentCostService.getDepartmentCost());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }


    @PutMapping("/update")
    public ResponseEntity<String> updateDepartmentCost(@RequestBody DepartmentCostDTO departmentCostDTO) {

        try {
            departmentCostService.updateDepartmentCost(departmentCostDTO);
            return ResponseEntity.ok("Department Cost updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating department cost: " + e.getMessage());
        }
    }
}


