package com.example.infraboxapi.project;

import com.example.infraboxapi.common.CommonService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/project")
@AllArgsConstructor
public class ProjectController {
    private final CommonService commonService;
    private final ProjectService projectService;

    @PostMapping("/create")
        public ResponseEntity<String> createProject(@ModelAttribute @Valid ProjectDTO projectDTO, BindingResult bindingResult) {

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
    public ResponseEntity<String> updateProject(@ModelAttribute @Valid ProjectDTO projectDTO, BindingResult bindingResult) {
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
}
