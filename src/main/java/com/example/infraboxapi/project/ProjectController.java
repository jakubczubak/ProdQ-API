package com.example.infraboxapi.project;

import com.example.infraboxapi.common.CommonService;
import com.example.infraboxapi.productionItem.ProductionItem;
import com.example.infraboxapi.productionItem.ProductionItemDTO;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/project")
@AllArgsConstructor
public class ProjectController {
    private final CommonService commonService;
    private final ProjectService projectService;

    @PostMapping("/create")
        public ResponseEntity<String> createProject(@Valid @RequestBody ProjectDTO projectDTO, BindingResult bindingResult) {

        if(bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }

        try {
            projectService.createProject(projectDTO);
            return ResponseEntity.ok("Project created");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating project: " + e.getMessage());
        }
    }

    @GetMapping("/get")
    public ResponseEntity<Iterable<Project>> getProjects() {
        try {
            return ResponseEntity.ok(projectService.getProjects());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<Project> getProject(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(projectService.getProject(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteProject(@PathVariable Integer id) {
        try {
            projectService.deleteProject(id);
            return ResponseEntity.ok("Project deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting project: " + e.getMessage());
        }
    }

    @PutMapping("/update")
    public ResponseEntity<String> updateProject(@Valid @RequestBody ProjectDTO projectDTO, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }
        try {
            projectService.updateProject(projectDTO);
            return ResponseEntity.ok("Project updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating project: " + e.getMessage());
        }
    }

    @PutMapping("/update/status/{id}")
    public ResponseEntity<String> updateProjectStatus(@PathVariable Integer id) {
        try {
            projectService.updateProjectStatus(id);
            return ResponseEntity.ok("Project status updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating project status: " + e.getMessage());
        }
    }

    @PutMapping("/update/hourlyRate/{id}")
    public ResponseEntity<String> updateHourlyRate(@PathVariable Integer id, @RequestBody double hourlyRate) {
        try {
            projectService.updateHourlyRate(id, hourlyRate);
            return ResponseEntity.ok("Project hourly rate updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating project hourly rate: " + e.getMessage());
        }
    }

    @PutMapping("/update/addProductionItem/{id}")
    public ResponseEntity<String> addProductionItem(@PathVariable Integer id, @RequestBody ProductionItem productionItem) {
        try {
            projectService.addProductionItem(id, productionItem);
            return ResponseEntity.ok("Production item added to project");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error adding production item to project: " + e.getMessage());
        }
    }
}
