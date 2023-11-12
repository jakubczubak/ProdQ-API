package com.example.infraboxapi.departmentCost;


import lombok.AllArgsConstructor;
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

        DepartmentCost departmentCost = departmentCostService.getDepartmentCost();
        return ResponseEntity.ok(departmentCost);
    }


    @PutMapping("/update")
    public ResponseEntity<String> updateDepartmentCost(@RequestBody DepartmentCostDTO departmentCostDTO) {

        departmentCostService.updateDepartmentCost(departmentCostDTO);
        return ResponseEntity.ok("Department Cost updated");
    }
}


